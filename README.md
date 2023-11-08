# sbt-teavm

[sbt](https://www.scala-sbt.org/) plugin for [TeaVM](https://teavm.org/).

Generate JavaScript and WebAssembly from Java bytecode.

## setup

### require
- install JDK 11 or later
- [sbt](https://www.scala-sbt.org/)

### optional
- [wasmtime](https://github.com/bytecodealliance/wasmtime) for run WebAssembly(WASI)
- [Node.js](https://Node.js.org) and Chrome for run WebAssembly and JavaScript

### install

#### `project/plugins.sbt`

```scala
addSbtPlugin("io.github.sbt-teavm" % "sbt-teavm" % version)
```

#### `build.sbt`

note: Maybe Scala 3 does not work due to default `lazy val` implementation use [`sun.misc.Unsafe`](https://github.com/lampepfl/dotty/blob/3.3.1/library/src/scala/runtime/LazyVals.scala)

```scala
scalaVersion := // recommend 2.13.x

enablePlugins(SbtTeaVM)
```

## usage

### basic sbt tasks

#### build

- `teavmJS`: build js
- `teavmWasm`: build WebAssembly (for web browser)
- `teavmWasi`: build WebAssembly (WASI)
- `teavmC`: build C (native)

#### run

- `teavmJS/run <args>`: require Node.js and Chrome
- `teavmWasm/run <args>`: require Node.js and Chrome
- `teavmWasi/run <args>`: require wasmtime

#### runMain

- `teavmJS/runMain`
- `teavmWasm/runMain`
- `teavmWasi/runMain`
