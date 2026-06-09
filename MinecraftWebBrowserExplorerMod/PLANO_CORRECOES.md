# Plano de Correções — MonitorMod (webbrowser)

> **Nota:** Este documento foi limpo e reflete apenas o código atual (abordagem BrowserScreen GUI com MonitorCommand). Itens do plano antigo (input in-world com crosshair, rato preso, WASD bloqueado) foram removidos por já não serem relevantes.

---

## Bugs Críticos

### 1. Shape não persiste ao NBT — servidor não consegue invalidar após reload
- `MonitorBlockEntity.shape: MonitorShape?` **nunca é salvo** em `saveAdditional()`
- Após reload: `shape = null` no servidor
- `invalidateMonitor()` faz `val s = shape ?: return` → retorna sem fazer nada
- Monitores ficam presos em `ACTIVE=true` para sempre
- `recalculateClientShape()` só corre no cliente (`if (level?.isClientSide != true) return`)
- **Impacto:** Em servidores multiplayer, monitores nunca são limpos; browsers acumulam-se
- **Ficheiro:** `MonitorBlockEntity.kt:154-163`, `MonitorBlockEntity.kt:37-42`

### 2. Browser leaked quando servidor invalida via neighborChanged
- `neighborChanged` só corre no servidor (`if (!level.isClientSide)`)
- Chama `validateNeighbors()` → `invalidateMonitor()`
- Dentro de `invalidateMonitor()`, `ClientBridge.onMonitorBreak` só corre se `wasClient == true`
- Como corre no servidor, `wasClient = false`, browser nunca é fechado
- **Impacto:** Cada quebra de bloco adjacente a um monitor ativo deixa um browser vazar no cliente
- **Ficheiro:** `MonitorBlockEntity.kt:76-83`, `MonitorBlock.kt:100`

### 3. Sem verificação de distância nos packets do servidor
- Nenhum handler (`MonitorAudioHandler`, `MonitorResolutionHandler`, `MonitorUpdateHandler`, `CommandSyncHandler`) verifica distância do jogador ao monitor
- Um cliente modificado pode alterar URL/resolução/audio de qualquer monitor no mundo
- **Impacto:** Segurança zero — qualquer jogador pode controlar qualquer monitor globalmente
- **Ficheiro:** `Network.kt:47-110`

### 4. ESC não funciona quando browser preview está focado
- `keyPressed()` no modo `browserFocused` nunca chama `super.keyPressed()`
- `super.keyPressed()` é o que processa ESC (keyCode 256)
- Jogador tem de fazer Tab primeiro para desfocar o browser, depois ESC
- **Impacto:** Mau UX — jogador fica preso no ecrã sem conseguir sair
- **Ficheiro:** `BrowserScreen.kt:342-365`

### 5. Comando não perde conexão quando monitor é partido
- `invalidateMonitor()` não notifica `MonitorCommandItem` conectados
- `MonitorCommandItem` guarda NBT com `connected=true` e posição
- Após o monitor partir, o item mantém brilho de encantamento e tooltip "Conectado a: x, y, z"
- **Impacto:** Informação enganosa ao jogador; item mantém stale NBT
- **Ficheiro:** `MonitorBlockEntity.kt:37-83`, `MonitorCommandItem.kt:46-49`

### 6. `audioMode`/`volume` não propagados aos child BEs em tryFormMonitor()
- `tryFormMonitor()` copia `width`, `height`, `pixelsPerBlock` para child BEs, mas **não** copia `audioMode` e `volume`
- Se o master tiver valores não default, os children mantêm 0 e 100
- Atualmente não causa falhas porque o GUI lê do master, mas é frágil
- **Ficheiro:** `MonitorBlockEntity.kt:126-136`

### 7. Browser nunca é criado ao carregar mundo com monitores existentes
- `ClientBridge.onMonitorFormed` só é chamado de `tryFormMonitor()`, que só corre em `MonitorBlock.onPlace()`
- Ao carregar um mundo, `onPlace` não dispara — os blocos já existem
- `onChunkLoad` antigo só logava "browser will be created on interaction" sem criar nada
- `BrowserScreen.init()` também não criava browser
- **Impacto:** Zero browsers na cache após relogar → preview vazio e render in-world ausente
- **Ficheiro:** `ClientSetup.kt:71-83` (agora corrigido), `BrowserScreen.kt:60-70` (agora corrigido)
- **Estado:** CORRIGIDO

### 8. Browser não é limpo quando monitor é partido (cliente não recebe notificação)
- `invalidateMonitor()` só chama `ClientBridge.onMonitorBreak` quando `wasClient == true`
- No servidor, `wasClient = false`, browser nunca é fechado
- Cliente recebe sync do NBT via `loadAdditional()` mas não notifica o bridge
- **Impacto:** Browser leak ao partir blocos do monitor
- **Ficheiro:** `MonitorBlockEntity.kt:176-184` (agora corrigido)
- **Estado:** CORRIGIDO

### 9. Preview não renderiza no GUI — textura CEF visível como cinzento
- `RenderSystem.bindTexture(texId)` não liga corretamente a textura ao shader no GUI
- Depth test ativo impede desenho correto no espaço 2D
- `RenderSystem.setShaderTexture(0, texId)` + `RenderSystem.disableDepthTest()` corrigem
- **Ficheiro:** `BrowserScreen.kt:307-317` (agora corrigido)
- **Estado:** CORRIGIDO

### 10. Preview não cabe na área disponível quando tamanho é alterado
- Campos de preview permitiam valores maiores que a área disponível (70% largura, full altura)
- `getScaledPreviewArea()` não clampava o container, causando overflow
- `onApplyPreviewSize()` não validava input
- **Ficheiro:** `BrowserScreen.kt:259-281`, `BrowserScreen.kt:149-156` (agora corrigido)
- **Estado:** CORRIGIDO

### 11. Comando não fica connectado no cliente após shift+left-click
- `CommandSyncHandler` modifica server-side stack, mas cliente não vê mudança imediata
- Sync do inventário pode demorar ticks → right-click falha porque `isConnected()` retorna false
- **Ficheiro:** `GameEvents.kt:29-33` — `MonitorCommandItem.connectTo()` chamado no cliente antes de enviar packet
- **Estado:** CORRIGIDO

### 12. Preview espelhada horizontalmente no BrowserScreen
- Preview no GUI usava U invertido (1→0) com comentário "CEF mirror — flip U"
- **Apenas a preview do GUI estava espelhada** — o render in-world `MonitorBlockEntityRenderer` estava correto (mantém o flip U, a textura CEF é mesmo espelhada)
- A preview precisa de UV normal (U 0→1) porque o vertex order + shader `POSITION_TEX` difere do `NEW_ENTITY` do renderer de blocos
- **Ficheiro:** `BrowserScreen.kt:309-312` (agora corrigido)
- **Estado:** CORRIGIDO

---

## Limitações Conhecidas

### A. DRM (Widevine) não suportado — filmes/séries não funcionam
- O MCEF não inclui os módulos CDM (Content Decryption Module) do Widevine/Google
- **Disney+, HBO Max, Amazon Prime Video, Netflix** — todos bloqueiam reprodução por DRM
- Mesmo com sessão iniciada, o ecrã fica preto ou mostra erro de "navegador não compatível"
- **Causa:** Ausência de licenciamento Widevine na build do CEF; o MCEF até pode ter suporte experimental, mas os binários Widevine não são distribuídos
- **Impacto:** Permanente — sem solução simples
- **Workaround:** Nenhum; o monitor serve para sites sem DRM (YouTube, Twitch, redes sociais, documentação)

---

## Bugs Médios

### 13. `tryFormMonitor()` chamado em ambos os lados — cliente modifica mundo
- `onPlace` corre em client e server
- Ambos chamam `tryFormMonitor()` que faz `level.setBlock()`
- Cliente modifica block states localmente antes do servidor enviar autoritativamente
- **Impacto:** Janela de desync visuais em multiplayer
- **Ficheiro:** `MonitorBlock.kt:76`, `MonitorBlockEntity.kt:93-151`

### 14. `renderTypeCache` nunca é limpa ao redimensionar browser
- `ClientBrowserCache.recreate()` fecha browser velho e cria novo (potencialmente texture ID diferente)
- `clearRenderTypeCache()` não é chamada durante resize
- Entradas antigas (keyed por texture ID velho) acumulam-se no mapa
- `clearRenderTypeCache()` só corre em `onMonitorBreak`
- **Impacto:** Pequeno leak de memória por cada mudança de resolução
- **Ficheiro:** `MonitorBlockEntityRenderer.kt:27-37`

### 15. `onCycleAudio()` destrói estado dos widgets via `rebuildWidgets()`
- `rebuildWidgets()` → `clearWidget()` + `init()` reconstrói TUDO
- Qualquer texto não aplicado nos campos é perdido
- Foco do teclado é resetado
- **Impacto:** Mau UX ao mudar modo de áudio
- **Ficheiro:** `BrowserScreen.kt:156-159`

---

## Problemas de Qualidade

- `MCEFRenderType.kt:16` — `@Suppress("INVISIBLE_REFERENCE")` para aceder a API privada do NeoForge; frágil em updates
- `BrowserScreen.kt:391` — `Quadruple` genérico em vez de data class nomeada (`PreviewArea`)
- `ClientBridge.kt:6-8` — Lambdas nuláveis; se o init do `ClientSetup` falhar, NPE no código common
- `BrowserScreen.kt:24` — Referência direta ao `MonitorBlockEntity` que pode ficar stale se o monitor for partido enquanto o GUI está aberto

---

## Funcionalidades em Falta (ainda relevantes)

- **Áudio real do CEF** — Campos e packet existem, mas não há captura/reprodução do stream de áudio do MCEF
- **Widevine/DRM** — Ausente; Netflix/Disney+ não funcionam
- **Validação de distância (6 chunks)** — Bug #3 acima
- **Comando perder conexão ao partir monitor** — Bug #5 acima
- **Shape persistente no NBT** — Bug #1 acima

---

## Itens Obsoletos (removidos do plano antigo)

Estes itens pertenciam à abordagem antiga de input in-world (crosshair, rato preso, WASD bloqueado) e foram substituídos pelo BrowserScreen GUI:

- §4 completo (Input in-world): substituído pelo preview + mouse events no BrowserScreen
- §6 ESC: substituído pelo `Screen.onClose()` nativo
- §3 "Left-click para agarrar controlos": substituído pela abertura do BrowserScreen
- `MonitorControllerBlock`: substituído pelo `MonitorCommandItem`
- `MonitorInputPacket`: desnecessário (GUI é client-only)
