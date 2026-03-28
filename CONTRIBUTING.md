# Contributing to Lock4j

感谢您有兴趣为 Lock4j 做出贡献！

## 开发环境设置

### 前置要求

- JDK 17+
- Maven 3.8+
- Git

### 克隆项目

```bash
git clone https://github.com/HK-hub/Lock4j.git
cd Lock4j
```

### 构建项目

```bash
mvn clean install
```

### 运行测试

```bash
mvn test
```

## 代码规范

### Java 代码风格

- 遵循 Google Java Style Guide
- 使用 4 个空格缩进
- 最大行宽 120 字符
- 使用 UTF-8 编码

### 命名规范

- 类名：PascalCase (如 `RedissonLockProvider`)
- 方法名：camelCase (如 `tryLock`)
- 常量：UPPER_SNAKE_CASE (如 `DEFAULT_LEASE_TIME`)
- 包名：小写 (如 `com.geek.lock.core`)

### 注释规范

- 公共 API 必须有 Javadoc 注释
- 复杂逻辑添加行内注释
- 使用中文注释

## 提交规范

### Commit Message 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建/工具

### 示例

```
feat(redis): 添加公平锁实现

- 实现基于 Redis Sorted Set 的公平锁
- 添加公平锁 Lua 脚本
- 添加单元测试

Closes #123
```

## Pull Request 流程

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### PR 检查清单

- [ ] 代码通过所有测试
- [ ] 新功能有对应的测试
- [ ] 公共 API 有 Javadoc 注释
- [ ] 更新了相关文档
- [ ] 遵循代码规范

## 添加新的 LockProvider

1. 创建新模块 `lock4j-xxx`
2. 实现 `LockProvider` 接口
3. 继承 `AbstractLockProvider`
4. 创建自动配置类
5. 添加测试
6. 更新文档

### 示例结构

```
lock4j-xxx/
├── src/main/java/com/geek/lock/xxx/
│   ├── annotation/XxxLock.java
│   ├── config/XxxLockAutoConfiguration.java
│   ├── config/XxxLockProperties.java
│   └── provider/XxxLockProvider.java
└── src/test/java/com/geek/lock/xxx/
    └── provider/XxxLockProviderTest.java
```

## 发布流程

1. 更新 CHANGELOG.md
2. 更新版本号
3. 创建 Git tag
4. 发布 GitHub Release
5. 部署到 Maven Central

## 联系方式

- Issues: https://github.com/HK-hub/Lock4j/issues
- Email: 3161880795@qq.com