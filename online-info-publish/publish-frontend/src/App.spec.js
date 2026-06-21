import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { useUserStore } from './stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: { template: '<div>Home</div>' } }]
})

describe('App.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders branding and external links', async () => {
    router.push('/')
    await router.isReady()
    
    const wrapper = mount(App, {
      global: { plugins: [router] }
    })
    
    expect(wrapper.text()).toContain('网上信息发布')
    expect(wrapper.text()).toContain('交易系统')
    expect(wrapper.text()).toContain('账户系统')
  })

  it('shows login/register buttons for GUEST', async () => {
    const wrapper = mount(App, {
      global: { plugins: [router] }
    })
    
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBe(2)
    expect(buttons[0].text()).toBe('登录')
    expect(buttons[1].text()).toBe('注册')
  })

  it('shows role label and logout button for logged-in users', async () => {
    const store = useUserStore()
    store.setToken('dummy')
    store.setRole('PREMIUM_VIP')
    
    const wrapper = mount(App, {
      global: { plugins: [router] }
    })
    
    expect(wrapper.find('.role-pill').exists()).toBe(true)
    expect(wrapper.find('.role-pill').text()).toBe('PREMIUM_VIP')
    expect(wrapper.find('.ghost-btn').text()).toBe('退出')
  })
})