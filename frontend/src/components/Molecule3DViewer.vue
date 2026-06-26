<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

const props = defineProps({
  atoms: {
    type: Array,
    default: () => []
  },
  lattice: {
    type: Array,
    default: () => []
  },
  showLattice: {
    type: Boolean,
    default: true
  },
  showBonds: {
    type: Boolean,
    default: true
  }
})

const host = ref(null)

const atomPalette = {
  Ag: 0xcbd5e1,
  As: 0xa78bfa,
  B: 0xf97316,
  Ba: 0x84cc16,
  Bi: 0x8b5cf6,
  Br: 0x8b5cf6,
  C: 0x22c55e,
  Ca: 0x38bdf8,
  Cd: 0x94a3b8,
  Cl: 0x16a34a,
  Co: 0x2563eb,
  Cu: 0xf59e0b,
  F: 0x06b6d4,
  Ga: 0x8fbf9f,
  Ge: 0x7aa7a8,
  H: 0xe2e8f0,
  I: 0x7c3aed,
  Li: 0xef4444,
  Mg: 0x0ea5e9,
  N: 0x60a5fa,
  Na: 0xf97316,
  Ni: 0x64748b,
  O: 0xf87171,
  P: 0xf59e0b,
  Pb: 0x78909c,
  S: 0xeab308,
  Si: 0x14b8a6,
  Sr: 0x8bc34a,
  Te: 0x9c7aa8,
  Ti: 0x94a3b8,
  Zn: 0x38bdf8
}

const displayRadii = {
  Ag: 0.23,
  As: 0.21,
  B: 0.17,
  Ba: 0.25,
  Bi: 0.24,
  Br: 0.2,
  C: 0.2,
  Ca: 0.22,
  Cd: 0.23,
  Cl: 0.2,
  Co: 0.2,
  Cu: 0.2,
  F: 0.16,
  Ga: 0.22,
  Ge: 0.22,
  H: 0.13,
  I: 0.22,
  Li: 0.18,
  Mg: 0.2,
  N: 0.19,
  Na: 0.2,
  Ni: 0.19,
  O: 0.19,
  P: 0.21,
  Pb: 0.24,
  S: 0.21,
  Si: 0.21,
  Sr: 0.24,
  Te: 0.23,
  Ti: 0.22,
  Zn: 0.2
}

const covalentRadii = {
  Ag: 1.45,
  As: 1.19,
  B: 0.85,
  Ba: 2.15,
  Bi: 1.48,
  Br: 1.14,
  C: 0.76,
  Ca: 1.76,
  Cd: 1.44,
  Cl: 1.02,
  Co: 1.26,
  Cu: 1.32,
  F: 0.57,
  Ga: 1.22,
  Ge: 1.2,
  H: 0.31,
  I: 1.33,
  Li: 1.28,
  Mg: 1.41,
  N: 0.71,
  Na: 1.66,
  Ni: 1.24,
  O: 0.66,
  P: 1.07,
  Pb: 1.46,
  S: 1.05,
  Si: 1.11,
  Sr: 1.95,
  Te: 1.38,
  Ti: 1.6,
  Zn: 1.22
}

let scene
let camera
let renderer
let controls
let moleculeGroup
let resizeObserver
let frameId

onMounted(async () => {
  await nextTick()
  initScene()
  drawMolecule()
  animate()
})

onBeforeUnmount(() => {
  cleanup()
})

watch(
  () => [props.atoms, props.lattice, props.showLattice, props.showBonds],
  async () => {
    await nextTick()
    drawMolecule()
  },
  { deep: true }
)

function initScene() {
  if (!host.value) return

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 1000)
  camera.position.set(0, 0, 6)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  host.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.8
  controls.enablePan = false

  moleculeGroup = new THREE.Group()
  scene.add(moleculeGroup)

  const ambient = new THREE.AmbientLight(0xffffff, 1.4)
  scene.add(ambient)

  const key = new THREE.DirectionalLight(0xffffff, 2)
  key.position.set(3, 4, 5)
  scene.add(key)

  const rim = new THREE.DirectionalLight(0x93c5fd, 1.2)
  rim.position.set(-4, -2, 3)
  scene.add(rim)

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host.value)
  resize()
}

function drawMolecule() {
  if (!moleculeGroup) return

  while (moleculeGroup.children.length) {
    const child = moleculeGroup.children.pop()
    child.geometry?.dispose()
    if (Array.isArray(child.material)) {
      child.material.forEach(material => material.dispose())
    } else {
      child.material?.dispose()
    }
  }

  if (!props.atoms.length) return

  const positions = normalizePositions(props.atoms)
  if (hasLattice()) {
    addLatticeCell(positions)
  } else {
    addBoundingBox(positions)
  }
  if (props.showBonds) addBonds(positions)
  addAtoms(positions)
  frameMolecule(positions)
}

function normalizePositions(atoms) {
  const rawVectors = atoms.map(atom => new THREE.Vector3(atom.x, atom.y, atom.z))
  const box = new THREE.Box3().setFromPoints(rawVectors)
  const center = box.getCenter(new THREE.Vector3())
  const size = box.getSize(new THREE.Vector3())
  const maxSpan = Math.max(size.x, size.y, size.z, hasLattice() ? latticeMaxSpan() : 1)
  const scale = 4 / maxSpan

  return atoms.map((atom, index) => ({
    ...atom,
    position: rawVectors[index].clone().sub(center).multiplyScalar(scale)
  }))
}

function addBoundingBox(atoms) {
  const box = new THREE.Box3().setFromPoints(atoms.map(atom => atom.position))
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const geometry = new THREE.BoxGeometry(
    Math.max(size.x, 0.5),
    Math.max(size.y, 0.5),
    Math.max(size.z, 0.5)
  )
  const edges = new THREE.EdgesGeometry(geometry)
  const material = new THREE.LineBasicMaterial({
    color: 0x93c5fd,
    transparent: true,
    opacity: 0.65
  })
  const cube = new THREE.LineSegments(edges, material)
  cube.position.copy(center)
  moleculeGroup.add(cube)
}

function hasLattice() {
  return props.showLattice
    && Array.isArray(props.lattice)
    && props.lattice.length === 3
    && props.lattice.every(row => Array.isArray(row) && row.length >= 3)
}

function addLatticeCell(atoms) {
  const rawVectors = props.atoms.map(atom => new THREE.Vector3(atom.x, atom.y, atom.z))
  const atomBox = new THREE.Box3().setFromPoints(rawVectors)
  const center = atomBox.getCenter(new THREE.Vector3())
  const size = atomBox.getSize(new THREE.Vector3())
  const maxSpan = Math.max(size.x, size.y, size.z, latticeMaxSpan(), 1)
  const scale = 4 / maxSpan

  const a = latticeVector(0)
  const b = latticeVector(1)
  const c = latticeVector(2)
  const corners = [
    new THREE.Vector3(0, 0, 0),
    a.clone(),
    b.clone(),
    c.clone(),
    a.clone().add(b),
    a.clone().add(c),
    b.clone().add(c),
    a.clone().add(b).add(c)
  ].map(point => point.sub(center).multiplyScalar(scale))

  const edgePairs = [
    [0, 1], [0, 2], [0, 3],
    [1, 4], [1, 5],
    [2, 4], [2, 6],
    [3, 5], [3, 6],
    [4, 7], [5, 7], [6, 7]
  ]
  const points = []
  edgePairs.forEach(([start, end]) => {
    points.push(corners[start], corners[end])
  })
  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  const material = new THREE.LineBasicMaterial({
    color: 0x7fb1ff,
    transparent: true,
    opacity: 0.72
  })
  moleculeGroup.add(new THREE.LineSegments(geometry, material))
}

function latticeVector(index) {
  const row = props.lattice[index] || []
  return new THREE.Vector3(Number(row[0]) || 0, Number(row[1]) || 0, Number(row[2]) || 0)
}

function latticeMaxSpan() {
  if (!hasLattice()) return 1
  const a = latticeVector(0)
  const b = latticeVector(1)
  const c = latticeVector(2)
  return Math.max(a.length(), b.length(), c.length(), 1)
}

function addAtoms(atoms) {
  atoms.forEach(atom => {
    const radius = displayRadii[atom.element] || 0.17
    const geometry = new THREE.SphereGeometry(radius, 32, 24)
    const material = new THREE.MeshStandardMaterial({
      color: atomPalette[atom.element] || 0xa78bfa,
      roughness: 0.38,
      metalness: 0.05
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.position.copy(atom.position)
    moleculeGroup.add(mesh)
  })
}

function addBonds(atoms) {
  for (let i = 0; i < atoms.length; i += 1) {
    for (let j = i + 1; j < atoms.length; j += 1) {
      const a = atoms[i]
      const b = atoms[j]
      if (a.element === 'H' && b.element === 'H') continue

      const rawDistance = new THREE.Vector3(a.x, a.y, a.z).distanceTo(new THREE.Vector3(b.x, b.y, b.z))
      const threshold = ((covalentRadii[a.element] || 0.7) + (covalentRadii[b.element] || 0.7)) * 1.22
      if (rawDistance <= threshold) {
        addBond(a.position, b.position)
      }
    }
  }
}

function addBond(start, end) {
  const direction = new THREE.Vector3().subVectors(end, start)
  const length = direction.length()
  if (!length) return

  const geometry = new THREE.CylinderGeometry(0.035, 0.035, length, 16)
  const material = new THREE.MeshStandardMaterial({
    color: 0x64748b,
    roughness: 0.45,
    metalness: 0.1
  })
  const mesh = new THREE.Mesh(geometry, material)
  mesh.position.copy(start).add(end).multiplyScalar(0.5)
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.normalize())
  moleculeGroup.add(mesh)
}

function frameMolecule(atoms) {
  const maxRadius = atoms.reduce((max, atom) => Math.max(max, atom.position.length()), 1)
  camera.position.set(maxRadius * 1.5, maxRadius * 0.9, Math.max(4.5, maxRadius * 3.1))
  camera.near = 0.1
  camera.far = Math.max(100, maxRadius * 12)
  camera.updateProjectionMatrix()
  controls.target.set(0, 0, 0)
  controls.update()
}

function resize() {
  if (!host.value || !renderer || !camera) return
  const width = Math.max(1, host.value.clientWidth)
  const height = Math.max(1, host.value.clientHeight)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

function animate() {
  frameId = window.requestAnimationFrame(animate)
  controls?.update()
  if (renderer && scene && camera) {
    renderer.render(scene, camera)
  }
}

function cleanup() {
  if (frameId) window.cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  controls?.dispose()

  if (moleculeGroup) {
    while (moleculeGroup.children.length) {
      const child = moleculeGroup.children.pop()
      child.geometry?.dispose()
      if (Array.isArray(child.material)) {
        child.material.forEach(material => material.dispose())
      } else {
        child.material?.dispose()
      }
    }
  }

  renderer?.dispose()
  if (renderer?.domElement?.parentNode) {
    renderer.domElement.parentNode.removeChild(renderer.domElement)
  }
}
</script>

<template>
  <div ref="host" class="molecule-viewer"></div>
</template>

<style scoped>
.molecule-viewer {
  width: 100%;
  height: 360px;
  min-height: 360px;
  overflow: hidden;
  border-radius: 8px;
  background: radial-gradient(
    circle at 50% 40%,
    var(--vs-card) 0%,
    color-mix(in srgb, var(--vs-card) 82%, var(--vs-primary)) 68%,
    var(--vs-border) 100%
  );
}

.molecule-viewer :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
