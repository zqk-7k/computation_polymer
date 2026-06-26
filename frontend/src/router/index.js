import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Detail from '../views/Detail.vue'
import DatasetRecords from '../views/DatasetRecords.vue'
import Login from '../views/Login.vue'
import AdminUsers from '../views/AdminUsers.vue'
import Explore from '../views/Explore.vue'
import Assistant from '../views/Assistant.vue'
import DataIntake from '../views/DataIntake.vue'

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/explore', name: 'explore', component: Explore },
  { path: '/assistant', name: 'assistant', component: Assistant },
  { path: '/intake', name: 'intake', component: DataIntake },
  { path: '/login', name: 'login', component: Login },
  { path: '/admin/users', name: 'admin-users', component: AdminUsers },
  { path: '/datasets/:id', name: 'dataset-records', component: DatasetRecords, props: true },
  { path: '/datasets/:id/records/:recordId', name: 'record-detail', component: Detail, props: true },
  { path: '/detail/:id', redirect: to => ({ name: 'dataset-records', params: { id: to.params.id } }) }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
