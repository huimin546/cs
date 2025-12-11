## 启动步骤

### 1. 数据库建立
- 在 PostgreSQL 终端中创建项目数据库：
  ```sql
  create database cs209a_final encoding='UTF8';
  ```
- 将连接信息写入项目根目录的 `.env`：
  ```dotenv
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cs209a_final
  SPRING_DATASOURCE_USERNAME=checker
  SPRING_DATASOURCE_PASSWORD=123456
  SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
  ```

### 2. 安装 Python 依赖（用于数据采集）
在仓库根目录执行：
```
pip install -r requirements.txt
```
当前依赖包含 `requests`、`psycopg[binary]` 以及 `python-dotenv`。脚本会在启动时自动读取根目录 `.env`，因此无需手动 `export` 环境变量；后续若新增依赖，请同步更新 `requirements.txt` 并重新安装。

### 3. 采集 Stack Overflow 数据
1. 申请 Stack Overflow API Key，并设为环境变量：
	```bash
	export STACKOVERFLOW_KEY=your_key
	# 如有 OAuth token，可再设置 STACKOVERFLOW_ACCESS_TOKEN
	```
2. 使用脚本直接写入数据库（默认模式）：
	```bash
	python scripts/fetch_so_threads.py --target 1200 --store=db
	```
	- 脚本会自动读取 `.env` 中的数据库参数；也可用 `--db-url/--db-user/--db-password` 覆盖。
	- 若想保留本地 JSON，改用 `--store=file --out data/raw`。
3. 完成后可在 psql 内检查：
	```sql
	select count(*) from questions;
	```

### 4. 启动 Spring Boot 后端
```
./mvnw spring-boot:run
```
首次启动会依据 JPA 实体自动建表，并在数据量未达阈值时从 `Sample_SO_data.zip` 匯入样本数据。

### 5. REST API：Topic Trends
- Endpoint：`GET /api/topics/trends`
- 查询参数：
	- `tags`：要分析的标签（可多值，逗号分隔）。若留空，预设为 `java,spring-boot,hibernate,multithreading,lambda,collections`。
	- `from` / `to`：时间区间（`YYYY-MM-DD`），默认统计过去三年到今天。
	- `bucket`：聚合粒度，`month`（默认）或 `year`。
	- `metric`：`questions`（按提问数量）或 `score`（按 Stack Overflow 得分）。

- 示例：
```
curl "http://localhost:8080/api/topics/trends?tags=spring-boot,lambda&from=2023-01-01&bucket=month&metric=score"
```
返回 JSON 结构包含每个标签的时间序列，可直接供前端图表使用。

- 返回：
    ```json
    {
        "tags":["spring-boot","lambda"],"from":"2023-01-01T00:00:00Z","to":"2025-12-            01T00:00:00Z","bucket":"month","metric":"SCORE","series":[{"tag":"spring-               boot","metric":"SCORE","points":[{"bucket":"2025-09-30T16:00:00Z","questionCount":1,"scoreSum":0,"value":0},{"bucket":"2025-10-31T16:00:00Z","questionCount":9,"scoreSum":4,"value":4}]},{"tag":"lambda","metric":"SCORE","points":[]}]
        }
    ```


### 6. REST API：Tag Co-occurrence
- Endpoint：`GET /api/topics/cooccurrence`
- 查询参数：
	- `top`：可选，返回前 N 大的标签组合（默认 10，范围 1~50）。
- 功能：遍历每个问题的标签集合，统计两两组合的共现次数，并按次数倒序返回。
- 示例：
	```
	curl "http://localhost:8080/api/topics/cooccurrence?top=15"
	```
- 返回：
	```json
	{
	  "top": 15,
	  "pairs": [
	    { "tagA": "spring-boot", "tagB": "hibernate", "questionCount": 37 },
	    { "tagA": "java", "tagB": "lambda", "questionCount": 21 }
	  ]
	}
	```

### 7. REST API：Multithreading Pitfall Analysis
- Endpoint：`GET /api/topics/multithreading/pitfalls`
- 功能：
	- 服务端通过 `AnalysisService` 读取带有 `java`、`multithreading`、`concurrency` 等标签的问题，并在标题+正文中匹配 `MULTITHREADING_PITFALL_KEYWORDS` 中的整词正则（当前 6 类）。
	- 匹配结果会根据命中次数降序排序，并支持通过 `top` 查询参数（默认 5，最小值 1）仅返回最常见的 Top N 多线程陷阱。
	- 不依赖任何外部 AI API，纯 Java `Pattern` 处理，响应中包含 `top` 与 `categories` 列表（每项含 `category`、`count`）。
- 示例：
	```
	curl "http://localhost:8080/api/topics/multithreading/pitfalls?top=5"
	```
- 返回：
	```json
	{
	  "top": 5,
	  "categories": [
	    { "category": "Thread Safety (General)", "count": 18 },
	    { "category": "Race Conditions", "count": 12 },
	    { "category": "Concurrent Modification", "count": 9 },
	    { "category": "Deadlocks", "count": 7 },
	    { "category": "Thread Lifecycle (Start/Join issues)", "count": 6 }
	  ]
	}
	```
- 实现位置：
	- 控制器：`cs209a.finalproject_demo.controller.MultithreadingAnalysisController`
	- 服务：`cs209a.finalproject_demo.service.AnalysisService`
	- DTO：`cs209a.finalproject_demo.service.dto.MultithreadingPitfallResponse`

### 8. REST API：Solvable vs Hard-to-Solve Questions
- Endpoint：`GET /api/topics/solvability/compare`
- 定义方式（会同时写入响应的 `criteria` 字段）：
	- **可解问题（Solvable）**：须存在被接受的回答，且该回答得分 ≥ 2，并且第一条回答需在 48 小时内出现。
	- **难解问题（Hard-to-solve）**：未被判定为可解，且满足以下任一条件：完全没有回答、没有被接受的回答、或第一条回答延迟超过 72 小时。
- 返回内容：
	- `totals`：可解/难解问题数量。
	- 至少 3 个 `factors`，比较两组在平均代码块数、含代码示例占比、平均标题长度、平均提问者声望、平均问题得分等维度的差异。
	- `solvableTopTags` / `hardTopTags`：各自最常出现的前三个标签及覆盖率（百分比）。
- 示例：
	```
	curl "http://localhost:8080/api/topics/solvability/compare"
	```
- 返回：
	```json
	{
	  "criteria": {
	    "solvable": {
	      "requiresAcceptedAnswer": true,
	      "minAcceptedAnswerScore": 2,
	      "maxFirstAnswerHours": 48
	    },
	    "hard": {
	      "markIfNoAnswers": true,
	      "markIfMissingAcceptedAnswer": true,
	      "minAnswerLatencyHours": 72
	    }
	  },
	  "totals": {
	    "solvableCount": 14,
	    "hardCount": 53
	  },
	  "factors": [
	    { "name": "平均代码块数量", "solvableValue": 2.6, "hardValue": 0.9, "unit": "blocks" },
	    { "name": "含代码示例占比", "solvableValue": 92.86, "hardValue": 37.74, "unit": "percent" },
	    { "name": "平均标题长度", "solvableValue": 68.4, "hardValue": 41.7, "unit": "characters" },
	    { "name": "平均提问者声望", "solvableValue": 1180.5, "hardValue": 210.3, "unit": "reputation" }
	  ],
	  "solvableTopTags": [
	    { "tag": "spring-boot", "percentage": 35.71 },
	    { "tag": "hibernate", "percentage": 28.57 },
	    { "tag": "java", "percentage": 21.43 }
	  ],
	  "hardTopTags": [
	    { "tag": "concurrency", "percentage": 33.96 },
	    { "tag": "java", "percentage": 28.3 },
	    { "tag": "async-task", "percentage": 16.98 }
	  ]
	}
	```
- 实现位置：
	- 控制器：`cs209a.finalproject_demo.controller.SolvabilityAnalysisController`
	- 服务：`cs209a.finalproject_demo.service.SolvabilityAnalysisService`
	- DTO：`cs209a.finalproject_demo.service.dto.*Solvability*`
