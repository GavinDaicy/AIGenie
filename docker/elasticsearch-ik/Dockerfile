# Elasticsearch 8.18 + IK 中文分词（插件版本须与 ES 一致）
# 安装说明见：https://github.com/infinilabs/analysis-ik
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.0

RUN bin/elasticsearch-plugin install --batch \
  "https://get.infini.cloud/elasticsearch/analysis-ik/8.18.0"
