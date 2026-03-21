const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,
  devServer: {
    port: 8080,
    proxy: {
      // 流式接口：禁止代理缓冲，逐块转发 SSE，否则会变成“一次性输出”
      '/genie/api/qa/ask/stream': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        selfHandleResponse: true,
        onProxyRes(proxyRes, req, res) {
          res.writeHead(proxyRes.statusCode, proxyRes.headers)
          proxyRes.on('data', (chunk) => res.write(chunk))
          proxyRes.on('end', () => res.end())
          proxyRes.on('error', (err) => {
            res.destroy(err)
          })
        }
      },
      '/genie/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
