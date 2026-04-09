const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  devServer: {
    port: 5000,
    proxy: {
      // 流式接口：禁止代理缓冲，逐块转发 SSE，否则会变成"一次性输出"
      // res.flush() 清空 compression 中间件 gzip 缓冲；setNoDelay 禁用 Nagle 算法
      '/genie/api/qa/ask/stream': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        selfHandleResponse: true,
        onProxyRes(proxyRes, req, res) {
          if (res.socket) res.socket.setNoDelay(true)
          res.writeHead(proxyRes.statusCode, proxyRes.headers)
          proxyRes.on('data', (chunk) => { res.write(chunk); if (res.flush) res.flush() })
          proxyRes.on('end', () => res.end())
          proxyRes.on('error', (err) => { res.destroy(err) })
        }
      },
      '/genie/api/agent/ask/stream': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        selfHandleResponse: true,
        onProxyRes(proxyRes, req, res) {
          if (res.socket) res.socket.setNoDelay(true)
          res.writeHead(proxyRes.statusCode, proxyRes.headers)
          proxyRes.on('data', (chunk) => { res.write(chunk); if (res.flush) res.flush() })
          proxyRes.on('end', () => res.end())
          proxyRes.on('error', (err) => { res.destroy(err) })
        }
      },
      '/genie/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
