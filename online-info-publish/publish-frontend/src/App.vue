<template>
  <div id="app" class="app-shell">
    <header class="topbar">
      <router-link class="brand" to="/home">网上信息发布</router-link>
      <router-link v-if="route.path !== '/'" class="ghost-btn back-nav" to="/">← 返回导航</router-link>

      <nav class="subsystem-links">
        <a :href="accountSysUrl" target="_blank" rel="noopener noreferrer">账户系统</a>
        <a :href="tradeClientUrl" target="_blank" rel="noopener noreferrer">交易客户端</a>
        <a :href="tradeMgmtUrl" target="_blank" rel="noopener noreferrer">交易管理</a>
      </nav>

      <div class="actions">
        <span v-if="userStore.globalUserId" class="role-pill">{{ roleLabel }}</span>
        <template v-if="!userStore.globalUserId">
          <a class="ghost-btn" :href="accountSysUrl + '/login'" target="_blank" rel="noopener noreferrer">登录</a>
          <input v-model="fundAccNoInput" class="fund-input"
                 placeholder="登录后粘贴资金账号" @keyup.enter="applyFundAccNo" />
        </template>
        <button v-else class="ghost-btn" type="button" @click="handleLogout">退出</button>
      </div>
    </header>

    <main class="page-shell">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from './stores/user'

const accountSysUrl = 'http://localhost:5173'
const tradeClientUrl = 'http://localhost:8090'
const tradeMgmtUrl = 'http://localhost:8081'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const fundAccNoInput = ref('')

const roleLabel = computed(() => {
  if (userStore.isPremiumVip) return 'PREMIUM_VIP'
  if (userStore.isStandard) return 'STANDARD'
  return 'GUEST'
})

const applyFundAccNo = () => {
  const v = fundAccNoInput.value.trim()
  if (!v) return
  userStore.setGlobalUserId(v)
  userStore.setRole('STANDARD')  // 登录成功后最低 STANDARD
  router.push('/home')
}

const handleLogout = () => {
  userStore.logout()
  fundAccNoInput.value = ''
  router.push('/')
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600&display=swap');

.app-shell {
  min-height: 100vh;
  background: #F8F9FA;
  color: #1b1c1c;
  font-family: 'Inter', sans-serif;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #E8E8E8;
  background: #ffffff;
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 1.25rem;
  font-weight: 700;
  color: #b7000c;
  font-family: 'IBM Plex Sans', sans-serif;
  text-decoration: none;
}

.subsystem-links {
  display: flex;
  gap: 12px;
}

.subsystem-links a {
  color: #666666;
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 500;
  transition: color 0.2s;
}

.subsystem-links a:hover {
  color: #b7000c;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-pill,
.primary-btn,
.ghost-btn {
  border-radius: 4px;
  padding: 8px 12px;
  border: 1px solid transparent;
  font-size: 0.92rem;
  cursor: pointer;
}

.role-pill {
  background: #ffdad5;
  border-color: #ffb4aa;
  color: #410001;
  font-weight: 600;
}

.primary-btn {
  background: #b7000c;
  color: #ffffff;
  font-weight: 700;
}

.ghost-btn {
  background: #fbf9f8;
  color: #1b1c1c;
  border-color: #E8E8E8;
}

.ghost-btn:hover {
  background: #f6f3f2;
}

.fund-input {
  width: 160px;
  height: 32px;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0 8px;
  font-size: 0.85rem;
  background: #fff;
  color: #333;
}
.fund-input::placeholder { color: #aaa; }

.page-shell {
  padding: 24px;
}
</style>
