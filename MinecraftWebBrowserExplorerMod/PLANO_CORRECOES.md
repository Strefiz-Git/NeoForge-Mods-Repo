# Plano de Correções — MonitorMod (webbrowser)

> **Nota:** Todos os bugs abaixo foram corrigidos na sessão de 2026-06-09. Sessão seguinte (2026-06-09 v2) implementou áudio real, volume, brightness >100, right-click connect, UX refresh/shortcuts/fullscreen, config system, culling, render limit e LRU cache.

---

## Bugs Críticos (Todos CORRIGIDOS)

### 1. Shape não persiste ao NBT — CORRIGIDO
- `MonitorBlockEntity.shape: MonitorShape?` agora é salvo em `saveAdditional()` com campos individuais (`shape_topLeft`, `shape_width`, `shape_height`, `shape_facing`, `shape_rightDir`, `shape_downDir`)
- Restaurado em `loadAdditional()` via `Direction.byName()` + `BlockPos.of()`
- `invalidateMonitor()` funciona após reload porque `shape` já não é `null`
- **Ficheiro:** `MonitorBlockEntity.kt:180-188` (save), `203-214` (load)

### 2. Browser leak quando servidor invalida via neighborChanged — CORRIGIDO
- `be.setChanged()` adicionado aos child BEs em `invalidateMonitor()` para marcar dirty e forçar sync ao cliente
- `loadAdditional()` no cliente deteta `wasActive && !isActive` e chama `ClientBridge.onMonitorBreak`
- **Ficheiro:** `MonitorBlockEntity.kt:69`, `MonitorBlockEntity.kt:215-222`

### 3. Sem verificação de distância nos packets do servidor — CORRIGIDO
- Função `checkDistance()` adicionada (constante `MAX_INTERACT_DIST_SQR = 96.0² = 6 chunks`)
- Chamada em todos os 5 handlers: `MonitorAudioHandler`, `MonitorBrightnessHandler`, `MonitorResolutionHandler`, `CommandSyncHandler`, `MonitorUpdateHandler`
- **Ficheiro:** `Network.kt:14-19`, `Network.kt:63,78,92,108,132`

### 4. ESC não funciona quando browser preview está focado — CORRIGIDO
- `keyPressed()` agora deteta `keyCode == 256` (GLFW_KEY_ESCAPE) mesmo em modo `browserFocused`
- Chama `onClose()` para sair do ecrã
- **Ficheiro:** `BrowserScreen.kt:479-483`

### 5. Comando não perde conexão quando monitor é partido — CORRIGIDO
- `invalidateMonitor()` no servidor itera todos os players, verifica `mainHandItem` e `offhandItem`
- Se o comando está conectado ao `oldMasterPos`, faz `disconnect()` e mostra mensagem "Monitor was broken"
- **Ficheiro:** `MonitorBlockEntity.kt:87-105`

### 6. `audioMode`/`volume` não propagados aos child BEs — CORRIGIDO
- `be.audioMode = audioMode` adicionado ao loop de child BEs em `tryFormMonitor()`
- `be.volume = volume` já existia
- **Ficheiro:** `MonitorBlockEntity.kt:154`

### 7. Browser nunca é criado ao carregar mundo — CORRIGIDO (anteriormente)
### 8. Browser não é limpo quando monitor é partido — CORRIGIDO (anteriormente)
### 9. Preview não renderiza — CORRIGIDO (anteriormente)
### 10. Preview não cabe na área — CORRIGIDO (anteriormente)
### 11. Comando não conecta no cliente — CORRIGIDO (anteriormente)
### 12. Preview espelhada — CORRIGIDO (anteriormente)

---

## Limitações Conhecidas

### A. DRM (Widevine) não suportado — filmes/séries não funcionam
- O MCEF não inclui os módulos CDM (Content Decryption Module) do Widevine/Google
- **Disney+, HBO Max, Amazon Prime Video, Netflix** — todos bloqueiam reprodução por DRM
- **Impacto:** Permanente — sem solução simples

---

## Bugs Médios (Todos CORRIGIDOS)

### 13. `tryFormMonitor()` chamado em ambos os lados — CORRIGIDO
- `if (level.isClientSide) return` adicionado no início de `tryFormMonitor()` para bloquear execução no cliente
- `if (!level.isClientSide)` adicionado em `MonitorBlock.onPlace()` antes de chamar `tryFormMonitor()`
- Cliente recebe shape via `loadAdditional()` → `!wasActive && isActive` → `ClientBridge.onMonitorFormed`
- **Ficheiro:** `MonitorBlockEntity.kt:117`, `MonitorBlock.kt:74`

### 14. `renderTypeCache` nunca é limpa ao redimensionar — CORRIGIDO
- `MonitorBlockEntityRenderer.clearRenderTypeCache()` chamado dentro de `ClientBrowserCache.recreate()`
- Limpeza acontece sempre que o browser é recriado (resize, reload)
- **Ficheiro:** `ClientBrowserCache.kt:36`

### 15. `onCycleAudio()` destrói estado dos widgets — CORRIGIDO

### 16. Brightness >100 causa overflow — CORRIGIDO
- Clamp máximo alterado de 100 para 500 em `BrowserConstants.kt`
- Fórmula de render: `brightness.coerceIn(1, MAX_BRIGHTNESS) / 100f` (já dava oversaturation)
- **Ficheiro:** `BrowserConstants.kt`, `Network.kt`, `BrowserScreen.kt`, `MonitorBlockEntityRenderer.kt`

### 17. Áudio do CEF não era capturado — CORRIGIDO
- `MCEFAudioEngine.kt` implementa `CefAudioHandler`
- Recebe PCM float em `onAudioStreamPacket()`, converte para 16-bit signed
- 4 OpenAL buffers por monitor em round-robin para streaming contínuo
- Suporta modo "Player" (relativo ao jogador) e "Center Block" (posicional no bloco)
- Volume controla `AL_GAIN` via `volume/100f` (clamped 0.0–3.0)
- **Ficheiro:** `MCEFAudioEngine.kt`

### 18. Right-click não conectava comando — CORRIGIDO
- `MonitorBlock.useWithoutItem()` agora detecta `MonitorCommandItem` e faz connect via right-click
- Shift+left-click removido
- **Ficheiro:** `MonitorBlock.kt`

### 19. Config system inexistente — CORRIGIDO
- `MonitorConfig.kt` com `ModConfigSpec` — client + common configs
- Registado em `MonitorMod.kt` via `container.registerConfig()`
- `maxResolutionPerBlock`, `maxMonitorsRendered`, `logBrowserLifecycle` em uso
- **Ficheiro:** `MonitorConfig.kt`, `MonitorMod.kt`, `BrowserConstants.kt`

### 20. Sem culling de monitores atrás do jogador — CORRIGIDO
- Dot product entre `camera.getLookVector()` e vetor `blockCenter - camera.position`
- Skip se `dot < -1.0` (monitor atrás do jogador)
- **Ficheiro:** `MonitorBlockEntityRenderer.kt`

### 21. Sem limite de render por frame — CORRIGIDO
- Contador `renderedThisFrame` incrementado a cada BE renderizado
- Se excede `MonitorConfig.maxMonitorsRendered`, saltar render
- Reset a cada game tick via `ClientTickEvent`
- **Ficheiro:** `MonitorBlockEntityRenderer.kt`, `ClientSetup.kt`

### 22. Browsers distantes nunca são limpos — CORRIGIDO
- `ClientBrowserCache.getAllPositions()` exposto
- A cada 20 ticks, monitores >12 chunks do jogador são destruídos (LRU)
- Áudio correspondente é deregistado
- **Ficheiro:** `ClientSetup.kt`, `ClientBrowserCache.kt`

### 23. Fullscreen não existia — CORRIGIDO
- Botão toggle no `BrowserScreen`
- ESC sai de fullscreen (volta a normal)
- Mouse mapping corrigido para fullscreen (`screenToBrowser()` + `renderCursor()`)
- **Ficheiro:** `BrowserScreen.kt`
- `audioButton: Button` guardado como field da classe em vez de ser anónimo
- `onCycleAudio()` já não chama `rebuildWidgets()` — apenas `updateAudioButtonLabel()` que faz `audioButton.setMessage()`
- **Ficheiro:** `BrowserScreen.kt:37`, `BrowserScreen.kt:116-121`, `BrowserScreen.kt:196-204`

---

## Problemas de Qualidade (não resolvidos)

- `MCEFRenderType.kt:16` — `@Suppress("INVISIBLE_REFERENCE")` para aceder a API privada do NeoForge; frágil em updates
- `BrowserScreen.kt` — `Quadruple` genérico em vez de data class nomeada (`PreviewArea`)
- `ClientBridge.kt:6-8` — Lambdas nuláveis; se o init do `ClientSetup` falhar, NPE no código common
- `BrowserScreen.kt` — Referência direta ao `MonitorBlockEntity` que pode ficar stale se o monitor for partido enquanto o GUI está aberto

---

## Funcionalidades em Falta (ainda relevantes)

- **Widevine/DRM** — Ausente; Netflix/Disney+ não funcionam
- **maxMonitorsPerPlayer** — Config definida mas não enforced no servidor
- **Notificação de crash** — MCEF tem `onCrashed()` callback, não usado
- **LOD para monitores distantes** — Podia reduzir resolução interna do CEF a partir de X chunks

---

## Itens Obsoletos (removidos do plano antigo)

Estes itens pertenciam à abordagem antiga de input in-world (crosshair, rato preso, WASD bloqueado) e foram substituídos pelo BrowserScreen GUI:

- §4 completo (Input in-world): substituído pelo preview + mouse events no BrowserScreen
- §6 ESC: substituído pelo `Screen.onClose()` nativo
- §3 "Left-click para agarrar controlos": substituído pela abertura do BrowserScreen
- `MonitorControllerBlock`: substituído pelo `MonitorCommandItem`
- `MonitorInputPacket`: desnecessário (GUI é client-only)
