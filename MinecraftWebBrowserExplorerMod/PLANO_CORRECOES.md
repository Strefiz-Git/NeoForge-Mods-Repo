# Plano de Correções — MonitorMod (webbrowser)

> **Nota:** Todos os bugs abaixo foram corrigidos na sessão de 2026-06-09.

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

- **Áudio real do CEF** — Campos e packet existem, mas não há captura/reprodução do stream de áudio do MCEF
- **Widevine/DRM** — Ausente; Netflix/Disney+ não funcionam

---

## Itens Obsoletos (removidos do plano antigo)

Estes itens pertenciam à abordagem antiga de input in-world (crosshair, rato preso, WASD bloqueado) e foram substituídos pelo BrowserScreen GUI:

- §4 completo (Input in-world): substituído pelo preview + mouse events no BrowserScreen
- §6 ESC: substituído pelo `Screen.onClose()` nativo
- §3 "Left-click para agarrar controlos": substituído pela abertura do BrowserScreen
- `MonitorControllerBlock`: substituído pelo `MonitorCommandItem`
- `MonitorInputPacket`: desnecessário (GUI é client-only)
