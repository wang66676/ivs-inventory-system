#!/usr/bin/env node
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync, statSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'

const root = execFileSync('git', ['-c', 'safe.directory=' + process.cwd(), 'rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim()
const owner = process.env.GITHUB_OWNER || 'wang66676'
const repo = process.env.GITHUB_REPO || 'ivs-inventory-system'
const tag = process.env.RELEASE_TAG || 'v1.9.8'
const message = process.env.RELEASE_MESSAGE || `release: ${tag}`
const token = process.env.GITHUB_TOKEN?.trim() || readFileSync('C:/Users/Administrator/.codex/github_token.txt', 'utf8').trim()
if (!token) throw new Error('GitHub token is missing')

const apiBase = `https://api.github.com/repos/${owner}/${repo}`
const headers = {
  Authorization: `Bearer ${token}`,
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'IVS-REST-Release',
  'Content-Type': 'application/json',
}

async function api(path, options = {}) {
  const response = await fetch(apiBase + path, { ...options, headers: { ...headers, ...(options.headers || {}) } })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`GitHub API ${options.method || 'GET'} ${path} failed: ${response.status} ${body.slice(0, 500)}`)
  }
  return response.status === 204 ? null : response.json()
}

function git(args, options = {}) {
  return execFileSync('git', ['-c', 'safe.directory=' + root, ...args], { cwd: root, ...options })
}

const rawTree = git(['ls-tree', '-r', '-z', 'HEAD'])
const entries = []
let offset = 0
while (offset < rawTree.length) {
  const end = rawTree.indexOf(0, offset)
  const record = rawTree.subarray(offset, end === -1 ? rawTree.length : end)
  const separator = record.indexOf(9)
  const header = record.subarray(0, separator).toString('utf8').split(' ')
  const path = record.subarray(separator + 1).toString('utf8')
  entries.push({ mode: header[0], type: header[1], sha: header[2], path })
  if (end === -1) break
  offset = end + 1
}

const ref = await api('/git/ref/heads/main')
const remoteCommit = await api(`/git/commits/${ref.object.sha}`)
const remoteTree = await api(`/git/trees/${remoteCommit.tree.sha}?recursive=1`)
const remoteBlobs = new Map((remoteTree.tree || []).filter((item) => item.type === 'blob').map((item) => [item.path, item.sha]))

const uploaded = new Map()
let uploadedCount = 0
for (const entry of entries) {
  if (entry.type !== 'blob') continue
  if (remoteBlobs.get(entry.path) === entry.sha) continue
  const content = readFileSync(join(root, entry.path)).toString('base64')
  const blob = await api('/git/blobs', { method: 'POST', body: JSON.stringify({ content, encoding: 'base64' }) })
  uploaded.set(entry.path, blob.sha)
  uploadedCount += 1
  if (uploadedCount % 25 === 0) console.log(`uploaded ${uploadedCount} blobs`)
}

function shaFor(entry) {
  return uploaded.get(entry.path) || entry.sha
}

const filesByDir = new Map()
for (const entry of entries) {
  if (entry.type !== 'blob') continue
  const dir = dirname(entry.path) === '.' ? '' : dirname(entry.path)
  if (!filesByDir.has(dir)) filesByDir.set(dir, [])
  filesByDir.get(dir).push(entry)
}

async function createTree(dir) {
  const tree = []
  const directFiles = filesByDir.get(dir) || []
  for (const file of directFiles) {
    tree.push({ path: file.path.slice(dir ? dir.length + 1 : 0), mode: file.mode, type: 'blob', sha: shaFor(file) })
  }
  const childDirs = new Set()
  for (const entry of filesByDir.keys()) {
    if (!entry || entry === dir) continue
    if (dir && !entry.startsWith(dir + '/')) continue
    const remainder = dir ? entry.slice(dir.length + 1) : entry
    const child = remainder.split('/')[0]
    if (child) childDirs.add(child)
  }
  for (const child of [...childDirs].sort()) {
    const childPath = dir ? `${dir}/${child}` : child
    const childTree = await createTree(childPath)
    tree.push({ path: child, mode: '040000', type: 'tree', sha: childTree.sha })
  }
  return api('/git/trees', { method: 'POST', body: JSON.stringify({ tree }) })
}

const rootTree = await createTree('')
const commit = await api('/git/commits', {
  method: 'POST',
  body: JSON.stringify({ message, tree: rootTree.sha, parents: [ref.object.sha] }),
})
await api('/git/refs/heads/main', { method: 'PATCH', body: JSON.stringify({ sha: commit.sha, force: true }) })
console.log(`main updated: ${ref.object.sha} -> ${commit.sha}; uploaded blobs: ${uploadedCount}`)

try {
  await api(`/git/ref/tags/${tag}`)
  console.log(`tag ${tag} already exists; left unchanged`)
} catch (error) {
  if (!String(error.message).includes('404')) throw error
  const tagObject = await api('/git/tags', {
    method: 'POST',
    body: JSON.stringify({ tag, message: `IVS ${tag}`, object: commit.sha, type: 'commit' }),
  })
  await api('/git/refs', { method: 'POST', body: JSON.stringify({ ref: `refs/tags/${tag}`, sha: tagObject.sha }) })
  console.log(`tag created: ${tag} -> ${commit.sha}`)
}
