<template>
  <a-card :bordered="false" title="组织架构">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="company" tab="公司">
        <a-form layout="inline" @finish="addCompany">
          <a-form-item label="编码"><a-input v-model:value="companyForm.code" /></a-form-item>
          <a-form-item label="名称"><a-input v-model:value="companyForm.name" /></a-form-item>
          <a-form-item><a-button type="primary" html-type="submit">新增公司</a-button></a-form-item>
        </a-form>
        <a-table v-resizable-columns class="table" row-key="id" :data-source="companies" :columns="baseColumns" :pagination="false" />
      </a-tab-pane>
      <a-tab-pane key="store" tab="门店">
        <a-form layout="inline" @finish="addStore">
          <a-form-item label="公司">
            <a-select v-model:value="storeForm.companyId" style="width: 220px">
              <a-select-option v-for="item in companies" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="编码"><a-input v-model:value="storeForm.code" /></a-form-item>
          <a-form-item label="名称"><a-input v-model:value="storeForm.name" /></a-form-item>
          <a-form-item><a-button type="primary" html-type="submit">新增门店</a-button></a-form-item>
        </a-form>
        <a-table v-resizable-columns class="table" row-key="id" :data-source="stores" :columns="storeColumns" :pagination="false" />
      </a-tab-pane>
      <a-tab-pane key="team" tab="团队">
        <a-form layout="inline" @finish="addTeam">
          <a-form-item label="公司">
            <a-select v-model:value="teamForm.companyId" style="width: 200px" @change="loadTeamStores">
              <a-select-option v-for="item in companies" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="门店">
            <a-select v-model:value="teamForm.storeId" style="width: 200px">
              <a-select-option v-for="item in teamStores" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="编码"><a-input v-model:value="teamForm.code" /></a-form-item>
          <a-form-item label="名称"><a-input v-model:value="teamForm.name" /></a-form-item>
          <a-form-item><a-button type="primary" html-type="submit">新增团队</a-button></a-form-item>
        </a-form>
        <a-table v-resizable-columns class="table" row-key="id" :data-source="teams" :columns="storeColumns" :pagination="false" />
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { organizationApi } from '../../api/organization'
import type { OrganizationItem } from '../../types/organization'

const activeTab = ref('company')
const companies = ref<OrganizationItem[]>([])
const stores = ref<OrganizationItem[]>([])
const teams = ref<OrganizationItem[]>([])
const teamStores = ref<OrganizationItem[]>([])
const companyForm = reactive({ code: '', name: '' })
const storeForm = reactive({ companyId: '', code: '', name: '' })
const teamForm = reactive({ companyId: '', storeId: '', code: '', name: '' })
const baseColumns = [
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '状态', dataIndex: 'status', key: 'status' },
]
const storeColumns = [
  ...baseColumns,
  { title: '公司ID', dataIndex: 'companyId', key: 'companyId' },
  { title: '门店ID', dataIndex: 'storeId', key: 'storeId' },
]

async function loadAll() {
  companies.value = await organizationApi.companies()
  stores.value = await organizationApi.stores()
  teams.value = await organizationApi.teams()
}
async function loadTeamStores() {
  teamForm.storeId = ''
  teamStores.value = teamForm.companyId ? await organizationApi.stores(teamForm.companyId) : []
}
async function addCompany() {
  try { await organizationApi.createCompany(companyForm.code, companyForm.name); message.success('公司已创建'); companyForm.code=''; companyForm.name=''; await loadAll() } catch(e){ message.error((e as Error).message) }
}
async function addStore() {
  try { await organizationApi.createStore(storeForm.companyId, storeForm.code, storeForm.name); message.success('门店已创建'); storeForm.code=''; storeForm.name=''; await loadAll() } catch(e){ message.error((e as Error).message) }
}
async function addTeam() {
  try { await organizationApi.createTeam(teamForm.companyId, teamForm.storeId, teamForm.code, teamForm.name); message.success('团队已创建'); teamForm.code=''; teamForm.name=''; await loadAll() } catch(e){ message.error((e as Error).message) }
}
onMounted(loadAll)
</script>

<style scoped>
.table { margin-top: 20px; }
</style>
