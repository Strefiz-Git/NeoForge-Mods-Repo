# Plano: Mod Monitor Browser — NeoForge 1.21.1 + Kotlin

## Visão Geral

Um mod que permite construir ecrãs multi-bloco no mundo do Minecraft que funcionam como browsers reais (Chromium). O jogador coloca blocos de monitor em forma de retângulo, ativa-o, e passa a ter um browser funcional dentro do jogo — com YouTube, Spotify, Netflix, e tudo o resto.

---

## Dependências Externas

| Dependência | Porquê |
|---|---|
| **MCEF** (Minecraft Chromium Embedded Framework) | Motor de browser Chromium embutido no Minecraft — é o que torna tudo possível |
| **KotlinForForge** | Suporte a Kotlin no NeoForge |

> MCEF é a peça central. É baseado em `java-cef` → `CEF` → Chromium, portanto suporta HTML5, CSS3, JavaScript, DRM (Widevine), e tudo o que o Chrome suporta — incluindo YouTube, Netflix, Spotify, etc.

---

## Blocos do Mod

### 1. `MonitorBlock` — O bloco de ecrã

O bloco principal que compõe o ecrã. Quando vários são colocados em retângulo válido, formam um único monitor unificado.

**Propriedades:**
- Tamanho mínimo: **2×2** blocos
- Tamanho máximo: **48×27** blocos (proporção 16:9 no máximo)
- Só forma monitor se a área for um **retângulo perfeito** e **plano** (todos os blocos na mesma face)
- Orientação: pode estar em qualquer parede (Norte, Sul, Este, Oeste) ou no chão/teto

**Estados do bloco (BlockState):**
```
FACING: north | south | east | west | up | down
PART: single | top_left | top | top_right | left | center | right | bottom_left | bottom | bottom_right
ACTIVE: true | false
```

**Comportamento:**
- Ao colocar, tenta automaticamente detetar e juntar-se a blocos adjacentes
- Se o retângulo for válido → todos ficam `ACTIVE = true` e registam-se no `MonitorBlockEntity` central (canto superior esquerdo)
- Se algum bloco for removido → todos ficam `ACTIVE = false` e o monitor destrói-se

---

### 2. `MonitorControllerBlock` — Bloco de controlo

Bloco separado, colocado em qualquer lugar, que ao ser clicado ativa o modo de interação com o monitor mais próximo (ou o ligado por Redstone).

**Alternativa:** Em vez de um bloco separado, shift+clique direito no próprio monitor abre o modo interativo.

---

## Arquitetura Técnica

```
MonitorBlock (N blocos)
    └── deteta retângulo válido
    └── elege "master" (canto superior esquerdo)
    └── cria/atualiza MonitorBlockEntity no master

MonitorBlockEntity (existe só no master)
    └── guarda: largura, altura, URL atual, estado
    └── tem referência ao MCEFBrowser (só client-side)
    └── MCEFBrowser renderiza para uma RenderTarget (framebuffer)
    └── essa textura é partilhada por todos os MonitorBlock na renderização

MonitorRenderer (BlockEntityRenderer)
    └── projeta a textura do browser nos N blocos
    └── calcula UV de cada bloco com base na sua posição relativa
    └── renderiza com shader sem lighting (o ecrã emite luz própria)

InputHandler (client-side)
    └── quando em modo interativo:
        └── captura mouse (GLFW cursor lock off)
        └── converte posição do rato → coordenada no browser
        └── envia MouseEvent e KeyEvent para o MCEFBrowser
        └── ESC → sai do modo interativo
```

---

## Fases de Desenvolvimento

---

### Fase 1 — Estrutura Base do Mod

**Objetivo:** Ter o projeto a compilar com as dependências certas.

**Tarefas:**
- Configurar `build.gradle.kts` com MCEF e KotlinForForge
- Criar o `neoforge.mods.toml` com `modLoader = "kotlinforforge"`
- Registar itens, blocos e block entities via `DeferredRegister`
- Criar o item `monitor_block` no criativo

**`build.gradle.kts` (dependências relevantes):**
```kotlin
repositories {
    maven {
        name = "Kotlin for Forge"
        setUrl("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven {
        name = "MCEF"
        setUrl("https://dl.cinemamod.com/") // repositório do MCEF
    }
}

dependencies {
    implementation("net.neoforged:neoforge:21.1.172")
    implementation("thedarkcolour:kotlinforforge-neoforge:6.0.0")
    implementation("org.cef:java-cef:...") // via MCEF
    compileOnly("net.neoforged.mcef:mcef:...") // versão para 1.21.1
}
```

---

### Fase 2 — Deteção do Retângulo

**Objetivo:** Quando o jogador coloca blocos, o mod verifica se formam um retângulo válido e ativa o monitor.

**Algoritmo de deteção (em Kotlin):**

```kotlin
fun findRectangle(origin: BlockPos, level: Level): MonitorShape? {
    // 1. A partir de 'origin', vai para o canto superior esquerdo
    // 2. Varre para a direita → mede largura
    // 3. Varre para baixo → mede altura
    // 4. Verifica que TODOS os blocos dentro são MonitorBlock
    // 5. Verifica que NÃO há MonitorBlock fora do retângulo detetado
    // 6. Retorna MonitorShape(topLeft, width, height) ou null
}

data class MonitorShape(
    val topLeft: BlockPos,
    val width: Int,   // 2..48
    val height: Int,  // 2..27
    val facing: Direction
)
```

**Regras de validação:**
- Mínimo 2×2, máximo 48×27
- Todos os blocos têm de ter o mesmo `FACING`
- Todos os blocos têm de estar no mesmo plano (mesma coordenada no eixo perpendicular)
- Forma tem de ser retangular preenchida (sem buracos)

**Quando ativar:**
- `onPlace` de qualquer `MonitorBlock` → chama `findRectangle`
- `onRemove` de qualquer `MonitorBlock` → invalida o monitor

---

### Fase 3 — Block Entity e Sincronização

**Objetivo:** O `MonitorBlockEntity` guarda o estado e sincroniza com o servidor.

```kotlin
class MonitorBlockEntity(pos: BlockPos, state: BlockState)
    : BlockEntity(MONITOR_BLOCK_ENTITY.get(), pos, state) {

    var width: Int = 1
    var height: Int = 1
    var currentUrl: String = "https://www.google.com"
    var isActive: Boolean = false

    // Só existe no cliente
    var browser: MCEFBrowser? = null  // inicializado no cliente

    override fun saveAdditional(tag: CompoundTag) {
        tag.putInt("width", width)
        tag.putInt("height", height)
        tag.putString("url", currentUrl)
    }

    override fun load(tag: CompoundTag) {
        width = tag.getInt("width")
        height = tag.getInt("height")
        currentUrl = tag.getString("url")
    }

    // Packet para sincronizar servidor → cliente
    override fun getUpdateTag() = saveAdditional(CompoundTag())
    override fun getUpdatePacket() = ClientboundBlockEntityDataPacket.create(this)
}
```

---

### Fase 4 — Integração com MCEF (Browser Real)

**Objetivo:** Criar um browser Chromium que renderiza para uma textura OpenGL.

```kotlin
// Inicialização do browser (só no cliente, em MonitorBlockEntity)
fun initBrowser() {
    if (!MCEFPlatform.isInitialized()) return

    browser = MCEFBrowser.createBrowser(
        url = currentUrl,
        width = width * BLOCK_PIXEL_WIDTH,   // ex: largura em px
        height = height * BLOCK_PIXEL_HEIGHT,
        transparent = false
    )
}

// A textura do browser fica disponível em:
// browser.renderer.texture  (NativeImage / OpenGL texture ID)
```

**Resolução do browser:**
- Cada bloco tem resolução de **120×120 px** internamente
- Um monitor 16×9 = 1920×1080 px → HD completo
- Um monitor 48×27 = 5760×3240 px (cuidado com VRAM — limitar a 1920×1080 máximo recomendado)

> **Nota sobre DRM (Netflix, Disney+, Spotify):** O MCEF usa CEF/Chromium que suporta Widevine. Para ativar, é necessário incluir a biblioteca `WidevineCdm` e configurar o CEF para a carregar. Sem isso, o YouTube funciona (não usa Widevine obrigatório), mas Netflix/Disney+ bloqueiam a reprodução. Esta é a parte mais complexa do mod.

---

### Fase 5 — Renderização nos Blocos

**Objetivo:** Projetar a textura do browser em todos os blocos do monitor.

```kotlin
class MonitorBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context)
    : BlockEntityRenderer<MonitorBlockEntity> {

    override fun render(
        entity: MonitorBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val browser = entity.browser ?: return
        val textureId = browser.renderer.textureID

        // Calcula qual a porção da textura que este bloco deve mostrar
        // com base na posição relativa do bloco dentro do retângulo
        val (uMin, uMax, vMin, vMax) = calculateUVForBlock(entity)

        // Renderiza um quad no face do bloco com a textura do browser
        // sem lighting (fullbright) para simular um ecrã iluminado
        renderScreenQuad(poseStack, buffer, textureId, uMin, uMax, vMin, vMax)
    }

    private fun calculateUVForBlock(entity: MonitorBlockEntity): UVCoords {
        // Posição relativa dentro do monitor (ex: bloco [2,1] num monitor 4×3)
        // U = coluna / largura_total
        // V = linha / altura_total
        val col = entity.relativeX.toFloat()
        val row = entity.relativeY.toFloat()
        return UVCoords(
            uMin = col / entity.width,
            uMax = (col + 1) / entity.width,
            vMin = row / entity.height,
            vMax = (row + 1) / entity.height
        )
    }
}
```

---

### Fase 6 — Input: Rato e Teclado

**Objetivo:** Quando o jogador está em modo interativo, redirecionar inputs para o browser.

**Como ativar o modo interativo:**
- Clique direito no monitor (com a mão livre) → entra no modo interativo
- O cursor do Minecraft some e aparece o cursor do browser
- ESC → sai do modo interativo (ou shift+clique direito)

```kotlin
object InputHandler {
    var activeMonitor: MonitorBlockEntity? = null

    // Chamado no RenderGameOverlayEvent (client tick)
    fun onClientTick(event: ClientTickEvent) {
        val monitor = activeMonitor ?: return
        val browser = monitor.browser ?: return

        // Converte posição do rato no ecrã → coordenada no browser
        val (mouseX, mouseY) = getMousePositionInMonitor(monitor)
        browser.sendMouseMove(mouseX.toInt(), mouseY.toInt(), false)
    }

    fun onMouseClick(button: Int, pressed: Boolean) {
        val browser = activeMonitor?.browser ?: return
        val (x, y) = getMousePositionInMonitor(activeMonitor!!)
        if (pressed)
            browser.sendMousePress(x.toInt(), y.toInt(), button)
        else
            browser.sendMouseRelease(x.toInt(), y.toInt(), button)
    }

    fun onKeyPress(keyCode: Int, scanCode: Int, modifiers: Int) {
        val browser = activeMonitor?.browser ?: return
        browser.sendKeyPress(keyCode, scanCode, modifiers)
    }

    fun onScroll(deltaX: Double, deltaY: Double) {
        val browser = activeMonitor?.browser ?: return
        val (x, y) = getMousePositionInMonitor(activeMonitor!!)
        browser.sendMouseWheel(x.toInt(), y.toInt(), deltaX.toInt(), deltaY.toInt())
    }

    // Cálculo da posição do rato no espaço do browser:
    // 1. Raycast da câmara → encontra o bloco do monitor que o rato está a apontar
    // 2. Determina a posição UV no face desse bloco
    // 3. Converte para coordenadas em píxeis no browser
    private fun getMousePositionInMonitor(monitor: MonitorBlockEntity): Pair<Double, Double> {
        // ... lógica de raycast
        TODO()
    }
}
```

---

### Fase 7 — Navegação e URL Bar

**Objetivo:** Interface de UI para introduzir URLs (opcional mas útil).

- **Shift + clique direito** no monitor → abre um `Screen` do Minecraft com campo de texto para URL
- O URL é enviado por packet ao servidor → atualizado na `BlockEntity` → sincronizado a todos os clientes
- No modo interativo, a barra de endereços do próprio browser é usável normalmente

---

### Fase 8 — Widevine / DRM (Netflix, Disney+, etc.)

**Objetivo:** Permitir reprodução de conteúdo protegido por DRM.

Esta é a fase mais técnica e dependente de fatores externos:

1. **Widevine CDM** — é a biblioteca proprietária da Google que o Chromium usa para DRM. Para incluir num mod, é necessário:
   - Ou distribuir os binários Widevine junto com o mod (termos de licença questionáveis)
   - Ou fazer o mod descarregá-los automaticamente do sistema (se o utilizador tiver Chrome instalado, os binários já existem localmente)

2. **Configuração no MCEF:**
```kotlin
// Antes de inicializar o browser, registar o Widevine CDM
val widevinePath = findWidevineFromChromeInstallation() 
    ?: downloadWidevine() // fallback

CefSettings.widevinePath = widevinePath
```

3. **Alternativa mais simples:** Usar um serviço intermédio como o [Invidious](https://invidious.io/) para YouTube (sem DRM), e aceitar que Netflix/Disney+ podem não funcionar sem Widevine.

> **Resumo prático:** YouTube e Spotify funcionam sem Widevine. Netflix, Disney+, Prime Video e HBO Max requerem Widevine ativo. Se o utilizador tiver o Google Chrome instalado, podes ler os binários Widevine localmente — é a abordagem mais limpa.

---

## Estrutura Final de Ficheiros

```
src/main/kotlin/com/exemplo/monitormod/
├── MonitorMod.kt                    ← classe principal @Mod
├── registry/
│   ├── BlockRegistry.kt             ← registo de blocos
│   ├── BlockEntityRegistry.kt       ← registo de block entities
│   └── ItemRegistry.kt              ← registo de itens
├── block/
│   ├── MonitorBlock.kt              ← bloco de monitor
│   └── MonitorBlockEntity.kt        ← block entity com MCEFBrowser
├── client/
│   ├── renderer/
│   │   └── MonitorBlockEntityRenderer.kt  ← renderização da textura
│   └── input/
│       └── InputHandler.kt          ← captura de rato e teclado
├── logic/
│   └── MonitorShapeDetector.kt      ← algoritmo de deteção de retângulo
└── network/
    ├── MonitorUpdatePacket.kt        ← sync servidor→cliente
    └── MonitorInputPacket.kt         ← input cliente→servidor (se necessário)

src/main/resources/
├── META-INF/neoforge.mods.toml
├── assets/monitormod/
│   ├── blockstates/monitor_block.json
│   ├── models/block/monitor_block.json
│   └── textures/block/
│       ├── monitor_off.png          ← textura quando inativo
│       └── monitor_border.png       ← borda do ecrã
└── data/monitormod/
    └── recipes/
        └── monitor_block.json
```

---

## Receita de Craft (sugestão)

```
[ Vidro ]  [ Vidro ]  [ Vidro ]
[ Ferro ]  [ Redstone] [ Ferro ]
[ Ferro ]  [ Ferro ]  [ Ferro ]
```
→ Dá 4× `monitor_block`

---

## Ordem de Implementação Recomendada

1. **Setup do projeto** com MCEF + KotlinForForge a compilar
2. **Bloco simples** que aparece no inventário e pode ser colocado
3. **Deteção de retângulo** — sem browser ainda, só validação visual (ex: partículas)
4. **Block Entity** com sincronização de dados básicos
5. **Browser estático** — um ecrã que abre `google.com` sem interatividade
6. **Renderização correta** em múltiplos blocos (UV mapping)
7. **Input de rato** — clicar no monitor funciona no browser
8. **Input de teclado** — escrever funciona
9. **Scroll** do rato
10. **URL Bar** via GUI do Minecraft
11. **Widevine** para DRM (Netflix, Disney+, etc.)
12. **Polimento** — texturas, sons, partículas ao ligar/desligar

---

## Limitações Conhecidas

| Limitação | Explicação |
|---|---|
| **Só funciona no cliente** | O browser só existe no lado do cliente. Em multiplayer, cada jogador tem a sua própria instância do browser — não é partilhado. |
| **Performance** | Um monitor de 1920×1080 consome VRAM e CPU como um browser real. Monitores muito grandes podem afetar o FPS. |
| **DRM** | Netflix/Disney+ requerem Widevine — vê a Fase 8. |
| **macOS** | MCEF tem suporte limitado em macOS. |
| **Linux** | Funciona mas pode haver problemas de compatibilidade de glibc dependendo da distro. |
| **Servidores dedicados** | O mod tem de ser marcado como `client-only` — não faz sentido em servidor puro sem clientes. |

---

## Referências

- [MCEF (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/mcef)
- [WebDisplays REMASTER (inspiração)](https://modrinth.com/mod/webdisplays-remaster)
- [KotlinForForge](https://github.com/thedarkcolour/KotlinForForge)
- [NeoForge Docs](https://docs.neoforged.net/)
- [java-cef (GitHub)](https://github.com/chromiumembedded/java-cef)
