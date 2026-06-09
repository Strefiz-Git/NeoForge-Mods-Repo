# Plano de Melhorias — MonitorMod (webbrowser)

> **Antes de implementar:** verificar logs do servidor + cliente, testar funcionalidade atual, depois implementar melhorias.

---

## Fase 0: Migração para Inglês (antes de tudo)

- `PLANO_CORRECOES.md` — já está em português, manter como está (docs internos)
- `PLANO_MELHORIAS.md` — já está em português, manter como está
- `CONTINUAR.txt` — conversa de desenvolvimento, manter como está
- **Código:** Logs (`LOGGER.info`) ficam em inglês (já estão)
- **Lang files:** `en_us.json` e `pt_pt.json` — manter ambos (já estão correctos)
- **Variáveis, comentários, commits** — manter inglês (já estão)
- **Conclusão:** O mod já está essencialmente em inglês no código. Documentos de planeamento em PT mantêm-se.

## Fase 0.1: Verificação de Funcionalidade

### 0.1 Testar fluxo completo
1. Colocar blocos do monitor (mín 6x3)
2. Clicar direito com MonitorCommand → conectar
3. Clicar direito com comando conectado → abrir BrowserScreen
4. Testar URL, resolução, brilho, áudio
5. Partir blocos → verificar limpeza
6. Sair e entrar → verificar persistência

### 0.2 Verificar logs
- Procurar erros no `logs/latest.log` do servidor e cliente
- Verificar se browsers são criados/limpos corretamente
- Verificar se shape persiste após reload

---

## Fase 1: Configurações do Mod (NeoForge Config) — ✅ FEITO

### 1.1 Sistema de Config (`MonitorConfig.kt`)
Usar sistema `net.neoforged.neoforge.common.ModConfigSpec` com ficheiro separado.

**Implementado em:**
- `MonitorConfig.kt` em `com.littlemine.webbrowser.config`
- Registado em `MonitorMod.kt` (constructor aceita `ModContainer`, usa `container.registerConfig()`)
- `BrowserConstants.kt` lê `MonitorConfig.maxResolutionPerBlock`
- `MonitorBlockEntityRenderer` usa `MonitorConfig.maxMonitorsRendered` + `logBrowserLifecycle`

**Configs geradas automaticamente:**
- `webbrowser-client.toml` — `renderScreens`, `maxMonitorsRendered`, `maxResolutionPerBlock`, `logBrowserLifecycle`
- `webbrowser-common.toml` — `maxMonitorsPerPlayer` (definida mas não enforced)

---

## Fase 2: Melhorias de Performance

### 2.1 Culling de Monitores Fora da Vista — ✅ FEITO
- `MonitorBlockEntityRenderer.render()` calcula dot product com `camera.getLookVector()`
- Skip se `dot < -1.0` (monitor atrás do jogador)
- **Ficheiro:** `MonitorBlockEntityRenderer.kt`

### 2.2 Limite de Monitores Renderizados por Frame — ✅ FEITO
- Contador `renderedThisFrame` no `MonitorBlockEntityRenderer`
- Se excede `MonitorConfig.maxMonitorsRendered`, saltar render
- Reset a cada game tick via `ClientTickEvent`
- **Ficheiro:** `MonitorBlockEntityRenderer.kt`, `ClientSetup.kt`

### 2.3 LOD (Level of Detail) para Monitores Distantes — ⏳ Pendente
- A partir de X chunks de distância, reduzir resolução interna do CEF browser
- Ou usar textura estática (screenshot de baixa resolução) para monitores longe
- Prioridade: baixa — implementar só se a performance for realmente um problema

### 2.4 Gestão de Memória dos Browsers CEF — ✅ FEITO
- `ClientBrowserCache.getAllPositions()` exposto
- A cada 20 ticks em `ClientTickEvent.Pre`, monitores >12 chunks do jogador são destruídos
- Áudio correspondente é deregistado via `MCEFAudioEngine.removeSource()`
- **Ficheiro:** `ClientSetup.kt`, `ClientBrowserCache.kt`

### 2.5 Evitar Alocação de Objetos no Render Loop — ⏳ Pendente
- **Atual:** `ByteBufferBuilder(256)` + `BufferBuilder()` criados a cada frame em cada BE renderizado
- **Mudança:** Reutilizar buffers estáticos ou usar pool de objetos
- Prioridade: baixa — micro-otimização, impacto mínimo

---

## Fase 3: Melhorias de UX — ✅ FEITO

### 3.1 Botão "Refresh" no GUI
- Botão "Ref" ao lado do "Go" no `BrowserScreen`
- Chama `browser.loadURL()` com a URL atual

### 3.2 Indicador de Loading
- "Loading..." mostrado quando `textureID <= 0` no preview e fullscreen

### 3.3 Atalhos de Teclado no BrowserScreen
- `F5` = Refresh
- `Ctrl+L` = focar URL field
- `Ctrl+W` = fechar ecrã

### 3.4 Notificação quando Browser Crasha — ⏳ Pendente
- MCEF tem callback `onCrashed()` — não usado atualmente

---

## Fase 4: Funcionalidades Pendentes

### 4.1 Áudio real do CEF — ✅ FEITO
- `MCEFAudioEngine.kt` implementa `CefAudioHandler`
- Recebe PCM float em `onAudioStreamPacket()`, converte para 16-bit signed
- 4 OpenAL buffers por monitor em round-robin para streaming contínuo
- Modos: "Player" (relativo ao jogador, `AL_SOURCE_RELATIVE`) e "Center Block" (posicional)
- Volume controla `AL_GAIN` via `volume/100f` (clamped 0.0–3.0)
- **Ficheiro:** `MCEFAudioEngine.kt`

### 4.2 Fullscreen — ✅ FEITO
- Botão "Fullscreen" no `BrowserScreen`
- ESC sai de fullscreen para modo normal (outro ESC fecha o ecrã)
- Mouse mapping corrigido (`screenToBrowser()` + `renderCursor()` usam toda a tela)
- **Ficheiro:** `BrowserScreen.kt`

---

## Estado Atual

```
✅ Fase 0 — Testar funcionalidade + verificar logs
✅ Fase 1 — Sistema de Config (MonitorConfig.kt)
✅ Fase 2.1 — Culling de monitores fora da vista
✅ Fase 2.2 — Limite de monitores renderizados
✅ Fase 2.4 — Gestão de memória (destruir browsers distantes)
✅ Fase 3 — Melhorias de UX (refresh, loading, atalhos)
✅ Fase 4.1 — Áudio real do CEF (OpenAL streaming)
✅ Fase 4.2 — Fullscreen
⏳ Fase 2.3 — LOD (pendente, baixa prioridade)
⏳ Fase 2.5 — Pool de objetos no render loop (pendente, baixa prioridade)
⏳ Fase 3.4 — Notificação de crash (pendente, baixa prioridade)
⏳ maxMonitorsPerPlayer — Config existe mas não enforced no servidor
```
