import { readFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { join } from 'node:path'

const owner = 'wang66676'
const repo = 'ivs-inventory-system'

async function readToken() {
  if (process.env.GITHUB_TOKEN?.trim()) return process.env.GITHUB_TOKEN.trim()
  const candidates = [
    join(process.env.USERPROFILE || homedir(), '.codex', 'github_token.txt'),
    'C:\\Users\\Administrator\\.codex\\github_token.txt',
  ]
  for (const file of candidates) {
    try {
      const token = (await readFile(file, 'utf8')).trim()
      if (token) return token
    } catch {
      // 尝试下一个位置
    }
  }
  throw new Error('未找到 GitHub Token')
}

async function main() {
  const rawUrl = process.argv[2]?.trim().replace(/\/+$/, '').replace(/\/api$/i, '')
  if (!rawUrl || !/^https:\/\/[a-z0-9.-]+(?::\d+)?$/i.test(rawUrl)) {
    console.error('用法: node sync-pages-api-url.mjs https://<固定域名或隧道域名>')
    process.exitCode = 2
    return
  }

  const token = await readToken()
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'User-Agent': 'IVS-Pages-Api-Sync',
    'Content-Type': 'application/json',
  }

  async function github(path, options = {}) {
    const response = await fetch(`https://api.github.com${path}`, {
      ...options,
      headers: { ...headers, ...(options.headers || {}) },
      signal: AbortSignal.timeout(30000),
    })
    const text = await response.text()
    if (!response.ok) {
      const error = new Error(`GitHub API ${response.status}: ${text.slice(0, 300)}`)
      error.status = response.status
      throw error
    }
    return text ? JSON.parse(text) : null
  }

  const desiredApiUrl = `${rawUrl}/api`
  const variablePath = `/repos/${owner}/${repo}/actions/variables/IVS_API_BASE_URL`
  let currentValue = null
  let exists = true

  try {
    const current = await github(variablePath)
    currentValue = current?.value ?? null
  } catch (error) {
    if (error.status !== 404) throw error
    exists = false
  }

  if (currentValue === desiredApiUrl) {
    console.log(`UNCHANGED ${desiredApiUrl}`)
    process.exitCode = 0
    return
  }

  const body = JSON.stringify({ name: 'IVS_API_BASE_URL', value: desiredApiUrl })
  if (exists) {
    await github(variablePath, { method: 'PATCH', body })
  } else {
    await github(`/repos/${owner}/${repo}/actions/variables`, { method: 'POST', body })
  }

  await github(`/repos/${owner}/${repo}/actions/workflows/pages.yml/dispatches`, {
    method: 'POST',
    body: JSON.stringify({ ref: 'main' }),
  })

  console.log(`UPDATED ${desiredApiUrl}`)
  process.exitCode = 10
}

main().catch((error) => {
  console.error(error.message || error)
  process.exitCode = 1
})
