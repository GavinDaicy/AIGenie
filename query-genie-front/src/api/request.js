import axios from 'axios'
import { Message } from 'element-ui'

const request = axios.create({
  baseURL: '/genie/api',
  timeout: 60000
})

request.interceptors.response.use(
  res => {
    const { data } = res
    if (data && typeof data.code !== 'undefined' && data.code !== 200 && data.code !== 0) {
      Message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data.data !== undefined ? data.data : data
  },
  err => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    Message.error(msg)
    return Promise.reject(err)
  }
)

export default request
