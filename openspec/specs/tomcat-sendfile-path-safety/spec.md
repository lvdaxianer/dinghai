# tomcat-sendfile-path-safety Specification

## Purpose

确保 Tomcat 原生 `sendfile` 仅接收当前运行环境可可靠传输的本地文件路径，并在不安全时保留 Web 层的流式读取语义。

## Requirements

### Requirement: Tomcat sendfile requires an ASCII ordinary path

Starter SHALL register Tomcat `sendfile` only when the final local file `Path` is non-null, is a
regular file with `LinkOption.NOFOLLOW_LINKS`, and every character in its absolute pathname is
ASCII. It MUST use the final local path rather than the logical object key for this decision.

#### Scenario: ASCII local file uses sendfile

- **WHEN** a Tomcat-capable plain request reads an ordinary local file whose absolute path is ASCII
- **THEN** the Starter registers the filename and requested byte interval for Tomcat `sendfile`

#### Scenario: Unicode local file bypasses sendfile

- **WHEN** a Tomcat-capable plain request reads an ordinary local file whose absolute path contains Chinese or another non-ASCII character
- **THEN** the Starter does not register any Tomcat `sendfile` attributes

#### Scenario: Unicode path streams its requested bytes

- **WHEN** a client reads `origin/479c7172-7832-49bf-96b4-52772b3db05b/端子标记槽-NJD-11L.gltf` from local storage
- **THEN** the HTTP response has its actual content length, a non-empty complete body, and correct single-Range semantics without a full-file JVM-memory load
