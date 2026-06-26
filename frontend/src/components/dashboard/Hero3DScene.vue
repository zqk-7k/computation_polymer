<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'

const host = ref(null)

let renderer
let scene
let camera
let worldGroup
let orbitalGroup
let nucleus
let polymerGroup
let particles
let particleGeometry
let latticeGroup
let resizeObserver
let intersectionObserver
let frameId = 0
let running = false
let reducedMotion = false
let lastTime = 0
let elapsed = 0

// Backbone / side-chain nodes and bonds are rebuilt every frame so the chain
// can flex like a real polymer instead of moving as one rigid block.
const backboneNodes = []
const sideNodes = []
const bondLinks = []

const pointer = { x: 0, y: 0 }
const pointerTarget = { x: 0, y: 0 }

function createOrbit(rx, ry, tiltZ, color, opacity = 0.42) {
  const points = []
  for (let i = 0; i <= 180; i += 1) {
    const t = (i / 180) * Math.PI * 2
    points.push(new THREE.Vector3(Math.cos(t) * rx, Math.sin(t) * ry, 0))
  }
  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  const material = new THREE.LineBasicMaterial({ color, transparent: true, opacity })
  const orbit = new THREE.Line(geometry, material)
  orbit.rotation.z = tiltZ
  return orbit
}

function createAtom(color, radius, { emissive = false } = {}) {
  const group = new THREE.Group()
  const sphere = new THREE.Mesh(
    new THREE.SphereGeometry(radius, 32, 24),
    new THREE.MeshStandardMaterial({
      color,
      roughness: 0.4,
      metalness: 0.06,
      emissive: emissive ? color : 0x000000,
      emissiveIntensity: emissive ? 0.18 : 0
    })
  )
  group.add(sphere)

  const halo = new THREE.Mesh(
    new THREE.SphereGeometry(radius * 1.2, 24, 16),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.1, depthWrite: false })
  )
  group.add(halo)
  return group
}

function createBond(color = 0x8aa0bd, radius = 0.03) {
  // Cylinder built with unit height; scaled along Y to the bond length each frame.
  const mesh = new THREE.Mesh(
    new THREE.CylinderGeometry(radius, radius, 1, 12),
    new THREE.MeshStandardMaterial({ color, roughness: 0.5, metalness: 0.1, transparent: true, opacity: 0.78 })
  )
  return mesh
}

function orientBond(mesh, a, b) {
  const dir = new THREE.Vector3().subVectors(b, a)
  const length = dir.length() || 1e-6
  mesh.position.copy(a).add(b).multiplyScalar(0.5)
  mesh.scale.set(1, length, 1)
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize())
}

function buildPolymer() {
  polymerGroup = new THREE.Group()
  const count = 11
  const spacing = 0.52
  const mid = (count - 1) / 2

  for (let i = 0; i < count; i += 1) {
    const x = (i - mid) * spacing
    const y = i % 2 === 0 ? 0.16 : -0.16
    const isCenter = i === mid
    const atom = createAtom(isCenter ? 0x2f6fed : 0x47576b, isCenter ? 0.2 : 0.15, { emissive: isCenter })
    polymerGroup.add(atom)
    backboneNodes.push({ mesh: atom, base: new THREE.Vector3(x, y, 0), phase: i * 0.66, current: new THREE.Vector3(x, y, 0) })
  }

  // Side groups (O / N) hung off alternating backbone atoms.
  const sideSpec = [
    { index: 2, color: 0xe3645c, offset: new THREE.Vector3(0, 0.42, 0.12), radius: 0.12 },
    { index: 4, color: 0x4a83e8, offset: new THREE.Vector3(0, -0.42, -0.1), radius: 0.12 },
    { index: 6, color: 0xe3645c, offset: new THREE.Vector3(0, 0.42, 0.1), radius: 0.12 },
    { index: 8, color: 0x4a83e8, offset: new THREE.Vector3(0, -0.42, 0.12), radius: 0.12 }
  ]
  sideSpec.forEach(spec => {
    const atom = createAtom(spec.color, spec.radius)
    polymerGroup.add(atom)
    const node = { mesh: atom, parent: spec.index, offset: spec.offset, current: new THREE.Vector3() }
    sideNodes.push(node)
  })

  // Bonds: backbone i—i+1, plus backbone—side.
  for (let i = 0; i < count - 1; i += 1) {
    const mesh = createBond()
    polymerGroup.add(mesh)
    bondLinks.push({ mesh, a: backboneNodes[i], b: backboneNodes[i + 1] })
  }
  sideNodes.forEach(node => {
    const mesh = createBond(0x9aa9bd, 0.024)
    polymerGroup.add(mesh)
    bondLinks.push({ mesh, a: backboneNodes[node.parent], b: node })
  })

  polymerGroup.position.set(0, -0.18, 0)
  polymerGroup.scale.setScalar(1.04)
  return polymerGroup
}

function buildLattice() {
  const group = new THREE.Group()
  const material = new THREE.LineBasicMaterial({ color: 0x4f86df, transparent: true, opacity: 0.1 })
  for (let x = -3; x <= 3; x += 1) {
    const geo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(x, -2, 0),
      new THREE.Vector3(x, 2, 0)
    ])
    group.add(new THREE.Line(geo, material))
  }
  for (let y = -2; y <= 2; y += 1) {
    const geo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-3, y, 0),
      new THREE.Vector3(3, y, 0)
    ])
    group.add(new THREE.Line(geo, material))
  }
  group.rotation.set(-0.92, 0.2, 0.12)
  group.position.set(0.2, -0.6, -1.4)
  return group
}

function buildParticles() {
  const count = 120
  const positions = new Float32Array(count * 3)
  const speeds = []
  for (let i = 0; i < count; i += 1) {
    positions[i * 3] = -3.4 + Math.random() * 6.8
    positions[i * 3 + 1] = -1.6 + Math.random() * 3.2
    positions[i * 3 + 2] = -0.8 + Math.random() * 1.8
    speeds.push(0.0016 + Math.random() * 0.0028)
  }
  particleGeometry = new THREE.BufferGeometry()
  particleGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  particleGeometry.userData.speeds = speeds
  const material = new THREE.PointsMaterial({
    color: 0x2f6fed,
    size: 0.028,
    transparent: true,
    opacity: 0.42,
    depthWrite: false
  })
  particles = new THREE.Points(particleGeometry, material)
  return particles
}

function setup() {
  if (!host.value) return
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch {
    return // No WebGL: leave the panel as its CSS gradient backdrop.
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  host.value.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(40, 1, 0.1, 80)
  camera.position.set(0, 0.15, 6.2)

  scene.add(new THREE.AmbientLight(0xeaf2ff, 1.2))
  const keyLight = new THREE.DirectionalLight(0xffffff, 1.35)
  keyLight.position.set(3.2, 4.2, 5)
  scene.add(keyLight)
  const cyanLight = new THREE.PointLight(0x16b5c8, 0.85, 14)
  cyanLight.position.set(-2.6, -0.8, 3.2)
  scene.add(cyanLight)
  const blueLight = new THREE.PointLight(0x2f6fed, 0.6, 14)
  blueLight.position.set(2.6, 1.6, 2.4)
  scene.add(blueLight)

  worldGroup = new THREE.Group()
  scene.add(worldGroup)

  // Electron orbitals wrap the highlighted central monomer.
  orbitalGroup = new THREE.Group()
  orbitalGroup.add(createOrbit(1.5, 0.52, 0.12, 0x2f6fed, 0.46))
  orbitalGroup.add(createOrbit(1.5, 0.52, Math.PI / 2.8, 0x16b5c8, 0.42))
  orbitalGroup.add(createOrbit(1.5, 0.52, -Math.PI / 2.8, 0x6d5bd7, 0.36))
  orbitalGroup.rotation.set(0.5, -0.18, 0)
  worldGroup.add(orbitalGroup)

  worldGroup.add(buildPolymer())
  worldGroup.add(buildLattice())
  scene.add(buildParticles())

  // Position nodes before the first paint so the chain never flashes as a
  // blob of atoms stacked at the origin.
  updateScene(0)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host.value)
  resize()

  host.value.addEventListener('pointermove', handlePointer)

  if (reducedMotion) {
    renderOnce()
  } else {
    intersectionObserver = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) start()
      else stop()
    }, { threshold: 0.05 })
    intersectionObserver.observe(host.value)
  }
}

function handlePointer(event) {
  if (!host.value) return
  const rect = host.value.getBoundingClientRect()
  pointerTarget.x = ((event.clientX - rect.left) / rect.width - 0.5) * 2
  pointerTarget.y = ((event.clientY - rect.top) / rect.height - 0.5) * 2
}

function updateScene(time) {
  if (!worldGroup) return

  backboneNodes.forEach(node => {
    node.current.set(
      node.base.x,
      node.base.y + Math.sin(time * 1.1 + node.phase) * 0.09,
      node.base.z + Math.cos(time * 0.8 + node.phase) * 0.07
    )
    node.mesh.position.copy(node.current)
  })
  sideNodes.forEach(node => {
    node.current.copy(backboneNodes[node.parent].current).add(node.offset)
    node.mesh.position.copy(node.current)
  })
  bondLinks.forEach(link => orientBond(link.mesh, link.a.current, link.b.current))

  if (orbitalGroup) {
    orbitalGroup.rotation.z = 0.1 + time * 0.16
    orbitalGroup.rotation.y = -0.18 + Math.sin(time * 0.4) * 0.08
  }
  if (polymerGroup) {
    polymerGroup.rotation.y = Math.sin(time * 0.3) * 0.18
    polymerGroup.position.y = -0.18 + Math.sin(time * 0.5) * 0.04
  }
  if (particleGeometry) {
    const position = particleGeometry.getAttribute('position')
    const speeds = particleGeometry.userData.speeds || []
    for (let i = 0; i < position.count; i += 1) {
      let x = position.getX(i) + speeds[i]
      if (x > 3.8) x = -3.8
      position.setX(i, x)
      position.setY(i, position.getY(i) + Math.sin(time + i) * 0.0007)
    }
    position.needsUpdate = true
  }

  // Mouse parallax (eased toward the pointer target).
  pointer.x += (pointerTarget.x - pointer.x) * 0.05
  pointer.y += (pointerTarget.y - pointer.y) * 0.05
  worldGroup.rotation.y = pointer.x * 0.34
  worldGroup.rotation.x = -pointer.y * 0.2
  camera.position.x = pointer.x * 0.4
  camera.lookAt(0, 0, 0)
}

function renderOnce() {
  renderer?.render(scene, camera)
}

function animate(now) {
  if (!running) return
  const delta = Math.min((now - lastTime) / 1000, 0.05)
  lastTime = now
  elapsed += delta
  updateScene(elapsed)
  renderOnce()
  frameId = requestAnimationFrame(animate)
}

function start() {
  if (running || reducedMotion) return
  running = true
  lastTime = performance.now()
  frameId = requestAnimationFrame(animate)
}

function stop() {
  running = false
  if (frameId) cancelAnimationFrame(frameId)
  frameId = 0
}

function resize() {
  if (!host.value || !renderer || !camera) return
  const rect = host.value.getBoundingClientRect()
  const width = Math.max(1, rect.width)
  const height = Math.max(1, rect.height)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  if (!running) renderOnce()
}

function cleanup() {
  stop()
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  host.value?.removeEventListener('pointermove', handlePointer)
  scene?.traverse(object => {
    object.geometry?.dispose?.()
    if (Array.isArray(object.material)) {
      object.material.forEach(material => material.dispose?.())
    } else {
      object.material?.dispose?.()
    }
  })
  renderer?.dispose()
  renderer?.domElement?.remove()
  backboneNodes.length = 0
  sideNodes.length = 0
  bondLinks.length = 0
}

onMounted(setup)
onBeforeUnmount(cleanup)
</script>

<template>
  <div ref="host" class="hero-scene" aria-hidden="true"></div>
</template>

<style scoped>
.hero-scene {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.hero-scene :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
