import { http } from './http'
import type {
  BatchImportResult, Category, Customer, PriceList, Product, ProductPrice, ProductSerial,
  Supplier, Warehouse,
} from '../types/masterdata'

export const categoryApi = {
  list: () => http.get<Category[]>('/ivs/product-categories').then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Category>('/ivs/product-categories', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<Category>(`/ivs/product-categories/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<Category>(`/ivs/product-categories/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<Category>(`/ivs/product-categories/${id}/enable`).then((r) => r.data),
  createAttrDef: (categoryId: string, body: Record<string, unknown>) =>
    http.post(`/ivs/product-categories/${categoryId}/attr-defs`, body).then((r) => r.data),
  deleteAttrDef: (attrDefId: string) => http.delete(`/ivs/product-categories/attr-defs/${attrDefId}`),
  delete: (id: string) => http.delete(`/ivs/product-categories/${id}`),
}

export const productApi = {
  list: (params?: { categoryId?: string; keyword?: string }) =>
    http.get<Product[]>('/ivs/products', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Product>('/ivs/products', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<Product>(`/ivs/products/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<Product>(`/ivs/products/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<Product>(`/ivs/products/${id}/enable`).then((r) => r.data),
  batchImport: (rows: Record<string, unknown>[]) =>
    http.post<BatchImportResult>(`/ivs/products/batch-import`, rows).then((r) => r.data),
  exportCsv: () => http.get(`/ivs/products/export`, { responseType: 'blob' }).then((r) => r.data),
}

export const serialApi = {
  list: (params?: { keyword?: string; status?: string; warehouseId?: string }) =>
    http.get<ProductSerial[]>('/ivs/product-serials', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<ProductSerial>('/ivs/product-serials', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<ProductSerial>(`/ivs/product-serials/${id}`, body).then((r) => r.data),
  moveStatus: (id: string, targetStatus: string) =>
    http.post<ProductSerial>(`/ivs/product-serials/${id}/status`, { targetStatus }).then((r) => r.data),
  batchImport: (commands: Record<string, unknown>[]) =>
    http.post<BatchImportResult>('/ivs/product-serials/batch-import', commands).then((r) => r.data),
}

export const warehouseApi = {
  list: () => http.get<Warehouse[]>('/ivs/warehouses').then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Warehouse>('/ivs/warehouses', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<Warehouse>(`/ivs/warehouses/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<Warehouse>(`/ivs/warehouses/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<Warehouse>(`/ivs/warehouses/${id}/enable`).then((r) => r.data),
}

export const supplierApi = {
  list: (params?: { supplierType?: string }) => http.get<Supplier[]>('/ivs/suppliers', { params }).then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Supplier>('/ivs/suppliers', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<Supplier>(`/ivs/suppliers/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<Supplier>(`/ivs/suppliers/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<Supplier>(`/ivs/suppliers/${id}/enable`).then((r) => r.data),
}

export const customerApi = {
  list: () => http.get<Customer[]>('/ivs/customers').then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<Customer>('/ivs/customers', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<Customer>(`/ivs/customers/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<Customer>(`/ivs/customers/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<Customer>(`/ivs/customers/${id}/enable`).then((r) => r.data),
}

export const priceListApi = {
  list: () => http.get<PriceList[]>('/ivs/price-lists').then((r) => r.data),
  create: (body: Record<string, unknown>) => http.post<PriceList>('/ivs/price-lists', body).then((r) => r.data),
  update: (id: string, body: Record<string, unknown>) => http.put<PriceList>(`/ivs/price-lists/${id}`, body).then((r) => r.data),
  disable: (id: string) => http.post<PriceList>(`/ivs/price-lists/${id}/disable`).then((r) => r.data),
  enable: (id: string) => http.post<PriceList>(`/ivs/price-lists/${id}/enable`).then((r) => r.data),
  saveLine: (id: string, body: Record<string, unknown>) =>
    http.post<PriceList>(`/ivs/price-lists/${id}/lines`, body).then((r) => r.data),
  removeLine: (id: string, lineId: string) =>
    http.delete<PriceList>(`/ivs/price-lists/${id}/lines/${lineId}`).then((r) => r.data),
  lookup: (params: { productId: string; customerType?: string; date?: string }) =>
    http.get<ProductPrice>('/ivs/price-lists/lookup', { params }).then((r) => r.data),
}

