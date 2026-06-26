package com.vaspshow.backend.service;

import com.vaspshow.backend.config.DatasetProperties;
import com.vaspshow.backend.dto.AtomCoordinateResponse;
import com.vaspshow.backend.dto.ConformerResponse;
import com.vaspshow.backend.dto.DatasetCardResponse;
import com.vaspshow.backend.dto.DatasetDetailResponse;
import com.vaspshow.backend.dto.GroupDetailResponse;
import com.vaspshow.backend.dto.GroupSummaryResponse;
import com.vaspshow.backend.exception.ApiException;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class AniDatasetService {

  public static final String DATASET_ID = "ani_gdb_s03";
  private static final String ROOT_GROUP = "gdb11_s03";
  private static final String UNIT_NOTE = "数据文件未提供单位，坐标、能量与 Rg 均按原始单位展示。";
  private static final Map<String, Double> ATOMIC_MASSES = Map.of(
      "H", 1.008,
      "C", 12.011,
      "N", 14.007,
      "O", 15.999
  );

  private final DatasetProperties properties;
  private final ResourceLoader resourceLoader;
  private volatile Path cachedAniPath;

  public AniDatasetService(DatasetProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  public List<DatasetCardResponse> listDatasets() {
    DatasetDetailResponse detail = getDatasetDetail(DATASET_ID);
    DatasetCardResponse card = new DatasetCardResponse(
        DATASET_ID,
        "ani_gdb_s03",
        "ANI-style HDF5 conformer energies",
        detail.totalConformers() + " 条构象 / " + detail.moleculeGroupCount() + " 个分子组",
        "本地 HDF5 分子构象数据集，包含 SMILES、元素序列、三维坐标和构象能量。",
        true,
        detail.moleculeGroupCount(),
        detail.totalConformers(),
        detail.minAtoms() + "-" + detail.maxAtoms(),
        detail.elements(),
        "ωB97X",
        "6-31G(d)"
    );
    return List.of(card);
  }

  public DatasetDetailResponse getDatasetDetail(String datasetId) {
    requireDataset(datasetId);
    try (HdfFile hdf = openFile()) {
      Group root = rootGroup(hdf);
      List<String> groupIds = moleculeGroupIds(root);
      List<GroupSummaryResponse> groups = new ArrayList<>();
      long totalConformers = 0;
      int minAtoms = Integer.MAX_VALUE;
      int maxAtoms = 0;
      Set<String> elements = new LinkedHashSet<>();

      for (String groupId : groupIds) {
        GroupSummaryResponse summary = readGroupSummary(hdf, groupId);
        groups.add(summary);
        totalConformers += summary.conformerCount();
        minAtoms = Math.min(minAtoms, summary.atomCount());
        maxAtoms = Math.max(maxAtoms, summary.atomCount());
        elements.addAll(summary.species());
      }

      return new DatasetDetailResponse(
          DATASET_ID,
          "ani_gdb_s03 分子构象数据集",
          ROOT_GROUP,
          "HDF5: coordinates + energies",
          totalConformers + " 条构象",
          "20 个 molecule group，字段来自 H5：coordinates、energies、smiles、species。",
          groups.size(),
          totalConformers,
          minAtoms == Integer.MAX_VALUE ? 0 : minAtoms,
          maxAtoms,
          new ArrayList<>(elements),
          groups,
          List.of()
      );
    }
  }

  public GroupDetailResponse getGroup(String datasetId, String groupId) {
    requireDataset(datasetId);
    try (HdfFile hdf = openFile()) {
      requireGroupExists(hdf, groupId);
      return new GroupDetailResponse(DATASET_ID, readGroupSummary(hdf, groupId));
    }
  }

  public ConformerResponse getConformer(String datasetId, String groupId, int index) {
    requireDataset(datasetId);
    try (HdfFile hdf = openFile()) {
      requireGroupExists(hdf, groupId);
      GroupSummaryResponse summary = readGroupSummary(hdf, groupId);
      if (index < 0 || index >= summary.conformerCount()) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "conformer index 超出范围: " + index + "，有效范围 0-" + (summary.conformerCount() - 1)
        );
      }

      double energy = readConformerEnergy(hdf, groupId, index);
      double[][] coordinates = readConformerCoordinates(hdf, groupId, index, summary.atomCount());
      double radiusOfGyration = radiusOfGyration(summary.species(), coordinates);
      List<AtomCoordinateResponse> atoms = new ArrayList<>();
      for (int i = 0; i < coordinates.length; i++) {
        atoms.add(new AtomCoordinateResponse(
            i + 1,
            summary.species().get(i),
            coordinates[i][0],
            coordinates[i][1],
            coordinates[i][2]
        ));
      }

      return new ConformerResponse(
          DATASET_ID,
          groupId,
          index,
          summary.smiles(),
          summary.species(),
          energy,
          radiusOfGyration,
          UNIT_NOTE,
          atoms
      );
    }
  }

  public String getConformerCsv(String datasetId, String groupId, int index) {
    ConformerResponse conformer = getConformer(datasetId, groupId, index);
    StringBuilder csv = new StringBuilder("index,element,x,y,z\n");
    for (AtomCoordinateResponse atom : conformer.atoms()) {
      csv.append(atom.index()).append(',')
          .append(atom.element()).append(',')
          .append(format(atom.x())).append(',')
          .append(format(atom.y())).append(',')
          .append(format(atom.z())).append('\n');
    }
    return csv.toString();
  }

  private GroupSummaryResponse readGroupSummary(HdfFile hdf, String groupId) {
    String base = "/" + ROOT_GROUP + "/" + groupId;
    Dataset coordinates = hdf.getDatasetByPath(base + "/coordinates");
    Dataset coordinatesHe = hdf.getDatasetByPath(base + "/coordinatesHE");
    Dataset energies = hdf.getDatasetByPath(base + "/energies");
    int[] coordDims = coordinates.getDimensions();
    int[] coordHeDims = coordinatesHe.getDimensions();
    List<Double> energyValues = flattenNumbers(energies.getDataFlat());
    if (energyValues.isEmpty()) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, groupId + " 没有可读取的 energies 数据");
    }

    double min = energyValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    double max = energyValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    double mean = energyValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);

    return new GroupSummaryResponse(
        groupId,
        readSmiles(hdf, groupId),
        readSpecies(hdf, groupId),
        coordDims.length > 1 ? coordDims[1] : 0,
        coordDims.length > 0 ? coordDims[0] : 0,
        coordHeDims.length > 0 ? coordHeDims[0] : 0,
        min,
        max,
        mean,
        sampleValues(energyValues, 80)
    );
  }

  private double readConformerEnergy(HdfFile hdf, String groupId, int index) {
    Dataset energies = hdf.getDatasetByPath("/" + ROOT_GROUP + "/" + groupId + "/energies");
    List<Double> values = flattenNumbers(energies.getDataFlat());
    if (index >= values.size()) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "无法读取 conformer energy: " + groupId + "/" + index);
    }
    return values.get(index);
  }

  private double[][] readConformerCoordinates(HdfFile hdf, String groupId, int index, int atomCount) {
    Dataset coordinates = hdf.getDatasetByPath("/" + ROOT_GROUP + "/" + groupId + "/coordinates");
    List<Double> values = flattenNumbers(coordinates.getDataFlat());
    int start = index * atomCount * 3;
    int end = start + atomCount * 3;
    if (values.size() < end) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "坐标维度异常: 期望至少 " + end + " 个数值，实际 " + values.size()
      );
    }
    double[][] coords = new double[atomCount][3];
    for (int i = 0; i < atomCount; i++) {
      coords[i][0] = values.get(start + i * 3);
      coords[i][1] = values.get(start + i * 3 + 1);
      coords[i][2] = values.get(start + i * 3 + 2);
    }
    return coords;
  }

  private String readSmiles(HdfFile hdf, String groupId) {
    Dataset smiles = hdf.getDatasetByPath("/" + ROOT_GROUP + "/" + groupId + "/smiles");
    return joinCharacters(smiles.getData());
  }

  private List<String> readSpecies(HdfFile hdf, String groupId) {
    Dataset species = hdf.getDatasetByPath("/" + ROOT_GROUP + "/" + groupId + "/species");
    return splitCharacters(species.getData());
  }

  private void requireDataset(String datasetId) {
    if (!DATASET_ID.equals(datasetId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未知数据集: " + datasetId);
    }
  }

  private void requireGroupExists(HdfFile hdf, String groupId) {
    Group root = rootGroup(hdf);
    if (!moleculeGroupIds(root).contains(groupId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "未知 molecule group: " + groupId);
    }
  }

  private List<String> moleculeGroupIds(Group root) {
    return root.getChildren().entrySet().stream()
        .filter(entry -> entry.getValue() instanceof Group)
        .map(Map.Entry::getKey)
        .filter(name -> name.matches("gdb11_s03-\\d+"))
        .sorted(Comparator.comparingInt(AniDatasetService::groupIndex))
        .toList();
  }

  private Group rootGroup(HdfFile hdf) {
    return (Group) hdf.getByPath("/" + ROOT_GROUP);
  }

  private HdfFile openFile() {
    Path path = cachedAniPath;
    if (path == null) {
      synchronized (this) {
        path = cachedAniPath;
        if (path == null) {
          path = resolveAniPath();
          cachedAniPath = path;
        }
      }
    }
    return new HdfFile(path);
  }

  private Path resolveAniPath() {
    String configuredValue = properties.getAniPath();
    if (configuredValue.startsWith("classpath:")) {
      return resolveClasspathResource(configuredValue);
    }

    Path configured = Paths.get(properties.getAniPath());
    if (configured.isAbsolute() && Files.exists(configured)) {
      return configured;
    }

    Path cwd = Paths.get("").toAbsolutePath();
    List<Path> candidates = List.of(
        cwd.resolve(configured).normalize(),
        cwd.resolve("..").resolve(configured).normalize(),
        cwd.resolve("../documents/data/ani_gdb_s03.h5").normalize()
    );
    return candidates.stream()
        .filter(Files::exists)
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
            "找不到 H5 数据文件: " + properties.getAniPath()));
  }

  private Path resolveClasspathResource(String location) {
    try {
      Resource resource = resourceLoader.getResource(location);
      if (!resource.exists()) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "找不到 H5 classpath 资源: " + location);
      }
      if (resource.isFile()) {
        return resource.getFile().toPath();
      }

      Path tempFile = Files.createTempFile("ani_gdb_s03-", ".h5");
      tempFile.toFile().deleteOnExit();
      try (InputStream inputStream = resource.getInputStream()) {
        Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return tempFile;
    } catch (IOException ex) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取 H5 classpath 资源失败: " + location);
    }
  }

  private static int groupIndex(String groupId) {
    int dash = groupId.lastIndexOf('-');
    return Integer.parseInt(groupId.substring(dash + 1));
  }

  private static List<Double> sampleValues(List<Double> values, int limit) {
    if (values.size() <= limit) {
      return values;
    }
    List<Double> samples = new ArrayList<>();
    double step = (values.size() - 1) / (double) (limit - 1);
    for (int i = 0; i < limit; i++) {
      samples.add(values.get((int) Math.round(i * step)));
    }
    return samples;
  }

  private static double radiusOfGyration(List<String> species, double[][] coordinates) {
    double totalMass = 0;
    double[] center = new double[3];
    for (int i = 0; i < coordinates.length; i++) {
      double mass = ATOMIC_MASSES.getOrDefault(species.get(i), 12.0);
      totalMass += mass;
      center[0] += mass * coordinates[i][0];
      center[1] += mass * coordinates[i][1];
      center[2] += mass * coordinates[i][2];
    }
    center[0] /= totalMass;
    center[1] /= totalMass;
    center[2] /= totalMass;

    double weighted = 0;
    for (int i = 0; i < coordinates.length; i++) {
      double mass = ATOMIC_MASSES.getOrDefault(species.get(i), 12.0);
      double dx = coordinates[i][0] - center[0];
      double dy = coordinates[i][1] - center[1];
      double dz = coordinates[i][2] - center[2];
      weighted += mass * (dx * dx + dy * dy + dz * dz);
    }
    return Math.sqrt(weighted / totalMass);
  }

  private static String joinCharacters(Object data) {
    StringBuilder builder = new StringBuilder();
    collectCharacters(data, builder);
    return builder.toString().trim();
  }

  private static List<String> splitCharacters(Object data) {
    List<String> values = new ArrayList<>();
    collectCharacterItems(data, values);
    return values.stream()
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }

  private static void collectCharacters(Object value, StringBuilder builder) {
    if (value == null) {
      return;
    }
    if (value instanceof Byte byteValue) {
      appendByte(builder, byteValue);
      return;
    }
    if (value instanceof Character character) {
      builder.append(character);
      return;
    }
    if (value instanceof String string) {
      builder.append(string);
      return;
    }
    Class<?> type = value.getClass();
    if (type.isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectCharacters(Array.get(value, i), builder);
      }
    }
  }

  private static void collectCharacterItems(Object value, List<String> values) {
    if (value == null) {
      return;
    }
    if (value instanceof Byte byteValue) {
      int unsigned = Byte.toUnsignedInt(byteValue);
      if (unsigned != 0) {
        values.add(Character.toString((char) unsigned));
      }
      return;
    }
    if (value instanceof Character character) {
      values.add(Character.toString(character));
      return;
    }
    if (value instanceof String string) {
      if (string.length() <= 1) {
        values.add(string);
      } else {
        for (int i = 0; i < string.length(); i++) {
          values.add(Character.toString(string.charAt(i)));
        }
      }
      return;
    }
    Class<?> type = value.getClass();
    if (type.isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectCharacterItems(Array.get(value, i), values);
      }
    }
  }

  private static void appendByte(StringBuilder builder, byte byteValue) {
    int unsigned = Byte.toUnsignedInt(byteValue);
    if (unsigned != 0) {
      builder.append((char) unsigned);
    }
  }

  private static List<Double> flattenNumbers(Object data) {
    List<Double> values = new ArrayList<>();
    collectNumbers(data, values);
    return values;
  }

  private static void collectNumbers(Object value, List<Double> values) {
    if (value == null) {
      return;
    }
    if (value instanceof Number number) {
      values.add(number.doubleValue());
      return;
    }
    Class<?> type = value.getClass();
    if (type.isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectNumbers(Array.get(value, i), values);
      }
    }
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.9f", value);
  }
}
