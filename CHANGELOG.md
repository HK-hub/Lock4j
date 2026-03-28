# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0-alpha.1] - 2026-03-28

### Added
- Initial alpha release
- Core lock abstraction with `LockProvider` interface
- Annotation-based lock support with `@Lock`
- Programmatic lock API with `LockTemplate`
- Multiple lock implementations:
  - Redisson (Redis)
  - RedisTemplate (Redis)
  - Zookeeper (Curator)
  - Etcd (Jetcd)
  - Local (JVM)
- Lock types: REENTRANT, FAIR, READ, WRITE
- Watchdog automatic renewal mechanism
- SpEL expression support for lock keys
- Custom key builders
- Failure handlers
- Lock event publishing
- Interceptor support
- Spring Boot auto-configuration
- Distributed context support (`LockDistributedContext`)
- Lock metrics collection (`LockMetricsCollector`)

### Fixed
- Etcd Provider reentrancy issue - now properly tracks lock ownership
- Zookeeper blocking issue - added default timeout protection
- RedisTemplate Watchdog issue - shared thread pool + correct lease time
- RedisTemplate now supports FAIR and READ/WRITE lock types

### Changed
- Unified version numbers across all modules
- Improved Watchdog implementation with shared thread pool
- Enhanced lock reentrancy tracking for all providers

### Security
- Proper lock ownership validation before unlock

## [Unreleased]

### Planned
- Redisson MultiLock support
- Redisson RedLock support
- More integration tests
- Performance benchmarks
- Maven Central publishing