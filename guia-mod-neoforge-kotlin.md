# Guia: Criar um Mod para NeoForge 1.21.1 com Kotlin

## Pré-requisitos

Antes de começar, instala o seguinte:

- **IntelliJ IDEA** (Community ou Ultimate) — [https://www.jetbrains.com/idea/](https://www.jetbrains.com/idea/)
- **JDK 21** — podes instalar diretamente pelo IntelliJ (ver secção abaixo)
- **Git** — [https://git-scm.com/](https://git-scm.com/)

---

## 1. Instalar o JDK 21 pelo IntelliJ

1. Abre o IntelliJ IDEA
2. Vai a **File → Project Structure → SDKs**
3. Clica em **+** → **Download JDK**
4. Seleciona a versão **21** (recomendado: Eclipse Temurin ou Amazon Corretto)
5. Clica em **Download**

> ⚠️ O NeoForge 1.21.1 **requer JDK 21**. Versões mais recentes (como o JDK 25) não são compatíveis.

---

## 2. Clonar o Skeleton Oficial

**Não uses o wizard "New Project" do IntelliJ.** Em vez disso, clona o projeto de base já configurado para NeoForge + Kotlin:

```bash
git clone --branch 1.21.10-neoforge https://github.com/thedarkcolour/KotlinModdingSkeleton.git meu-mod
cd meu-mod
```

Este skeleton já inclui todas as configurações necessárias para o KotlinForForge.

---

## 3. Abrir o Projeto no IntelliJ

1. Abre o IntelliJ IDEA
2. Clica em **Open** (não em "New Project")
3. Navega até à pasta `meu-mod`
4. Seleciona o ficheiro **`build.gradle.kts`**
5. Quando perguntado, escolhe **"Open as Project"**
6. Aguarda o Gradle sincronizar (pode demorar alguns minutos na primeira vez)

---

## 4. Estrutura do Projeto

Após abrir, o projeto terá esta estrutura:

```
meu-mod/
├── build.gradle.kts          ← configuração do Gradle
├── settings.gradle.kts       ← nome e repositórios do projeto
├── gradle.properties         ← versões do Minecraft, NeoForge e do mod
├── src/main/
│   ├── kotlin/               ← o teu código Kotlin
│   └── resources/
│       └── META-INF/
│           └── neoforge.mods.toml  ← metadados do mod
```

---

## 5. Configurar o `gradle.properties`

Edita o ficheiro `gradle.properties` com os dados do teu mod:

```properties
# Minecraft / NeoForge
minecraft_version=1.21.1
neoforge_version=21.1.172

# Informações do mod
mod_id=meumod
mod_name=Meu Mod
mod_version=1.0.0
mod_group=com.exemplo.meumod

# Java
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

---

## 6. Configurar o `build.gradle.kts`

O ficheiro deve ter este aspeto (já deverá estar próximo disto no skeleton):

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
    id("net.neoforged.gradle.userdev") version "7.0.171"
}

version = project.property("mod_version") as String
group = project.property("mod_group") as String

base {
    archivesName.set(project.property("mod_id") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    maven {
        name = "Kotlin for Forge"
        setUrl("https://thedarkcolour.github.io/KotlinForForge/")
    }
}

dependencies {
    implementation("net.neoforged:neoforge:${project.property("neoforge_version")}")

    // KotlinForForge — fornece stdlib, coroutines, serialization, etc.
    implementation("thedarkcolour:kotlinforforge-neoforge:6.0.0")
}

runs {
    configureEach {
        systemProperty("forge.logging.console.level", "debug")
        modSource(project.sourceSets.main.get())
    }

    create("client")
    create("server") {
        programArguments("--nogui")
    }
}
```

---

## 7. Configurar o `neoforge.mods.toml`

Este ficheiro fica em `src/main/resources/META-INF/neoforge.mods.toml`.

> ⚠️ O campo `modLoader` **tem de ser `"kotlinforforge"`** — se ficares com `"javafml"`, o mod não vai carregar.

```toml
modLoader = "kotlinforforge"
loaderVersion = "[6.0,)"
license = "MIT"

[[mods]]
modId = "meumod"
version = "1.0.0"
displayName = "Meu Mod"
description = "O meu primeiro mod em Kotlin!"

[[dependencies.meumod]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.meumod]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.meumod]]
modId = "kotlinforforge"
type = "required"
versionRange = "[6.0,)"
ordering = "NONE"
side = "BOTH"
```

---

## 8. Classe Principal do Mod

Cria o ficheiro `MeuMod.kt` em `src/main/kotlin/com/exemplo/meumod/`:

```kotlin
package com.exemplo.meumod

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.ModContainer
import org.apache.logging.log4j.LogManager
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod("meumod")
class MeuMod(modContainer: ModContainer) {

    companion object {
        private val LOGGER = LogManager.getLogger("meumod")
    }

    init {
        val bus: IEventBus = MOD_BUS

        bus.addListener(::onCommonSetup)
        bus.addListener(::onClientSetup)

        LOGGER.info("Meu Mod carregado!")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("Setup comum executado")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.info("Setup do cliente executado")
    }
}
```

> 💡 Usa `thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS` — **não** o `FMLJavaModLoadingContext` do Forge normal.

---

## 9. Correr o Mod

No terminal dentro da pasta do projeto, ou pelo IntelliJ:

```bash
# Gerar configurações de run para o IntelliJ
./gradlew genIntellijRuns

# Correr o cliente do Minecraft com o mod
./gradlew runClient

# Correr um servidor dedicado
./gradlew runServer
```

Após `genIntellijRuns`, aparecerão configurações de run no canto superior direito do IntelliJ (▶).

---

## 10. Compilar o Mod (`.jar`)

```bash
./gradlew build
```

O ficheiro `.jar` ficará em `build/libs/`. É esse ficheiro que colocas na pasta `mods/` do Minecraft.

---

## 11. Licença

Para a maioria dos mods, usa **MIT** — é a licença padrão na comunidade, permite uso em modpacks, e é simples.

No GitHub, quando criares o repositório, seleciona **MIT License**. Também podes adicionar manualmente um ficheiro `LICENSE` na raiz com o seguinte conteúdo (substituindo o ano e o nome):

```
MIT License

Copyright (c) 2025 <O Teu Nome>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Resumo Rápido

| O quê | Valor |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.172 |
| KotlinForForge | 6.0.0 |
| Kotlin | 2.2.20 |
| JDK | 21 |
| `modLoader` no toml | `"kotlinforforge"` |
| Mod Bus | `thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS` |
| Licença recomendada | MIT |

---

## Links Úteis

- [KotlinForForge (GitHub)](https://github.com/thedarkcolour/KotlinForForge)
- [KotlinModdingSkeleton (GitHub)](https://github.com/thedarkcolour/KotlinModdingSkeleton)
- [NeoForge Docs](https://docs.neoforged.net/)
- [Discord KotlinForForge](https://discord.gg/tmVmZtx)
