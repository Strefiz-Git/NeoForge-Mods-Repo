# Plano de Melhorias — MonitorMod (webbrowser)

> **Antes de implementar:** verificar logs do servidor + cliente, testar funcionalidade atual, depois implementar melhorias.

---

## Fase 0: Verificação de Funcionalidade

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

## Fase 1: Configurações do Mod (NeoForge Config)

### 1.1 Sistema de Config (`MonitorConfig.kt`)
Usar sistema `net.neoforged.neoforge.common.ModConfigSpec` com ficheiro separado.

**Categorias:**

#### Client Config (`webbrowser-client.toml`)
| Chave | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `renderScreens` | boolean | `true` | Toggle global para renderizar/ocultar todos os ecrãs no mundo |
| `maxRenderDistance` | int | `6` | Distância máxima (chunks) para renderizar ecrãs |
| `maxMonitorsRendered` | int | `8` | Máximo de monitores renderizados ao mesmo tempo (0 = ilimitado) |
| `maxResolutionPerBlock` | int | `120` | Máximo de pixels por bloco que um monitor pode ter |
| `browserHeapMemoryMB` | int | `512` | Memória máxima para browsers CEF (evitar crashes) |
| `enableBrowserPreviews` | boolean | `true` | Mostrar preview do browser no GUI |
| `logBrowserLifecycle` | boolean | `false` | Log detalhado de criação/destruição de browsers |

#### Common Config (`webbrowser-common.toml`)
| Chave | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `maxMonitorsPerPlayer` | int | `-1` | Máximo de monitores que um jogador pode ter ativos (-1 = ilimitado) |

### 1.2 Implementação
- Criar `MonitorConfig.kt` em `com.littlemine.webbrowser.config`
- Registrar no `MonitorMod.kt` via `ModLoadingContext.get().registerConfig()`
- Aplicar limites no `BrowserConstants.kt` (usar valores do config)
- Passar `maxMonitorsRendered` e `maxResolutionPerBlock` para os respetivos locais

---

## Fase 2: Melhorias de Performance

### 2.1 Culling de Monitores Fora da Vista
- **Atual:** `shouldRenderOffScreen()` retorna `true` — renderiza mesmo atrás do jogador
- **Mudança:** Calcular se o monitor está dentro do frustum/cone de visão do jogador
- Usar `Camera.isActive()` + direção do jogador para skip de monitores atrás

### 2.2 Limite de Monitores Renderizados por Frame (`maxMonitorsRendered`)
- No `MonitorBlockEntityRenderer.render()`, contar monitores renderizados este frame
- Se exceder `maxMonitorsRendered`, saltar render (manter preview GUI funcional)

### 2.3 LOD (Level of Detail) para Monitores Distantes
- A partir de X chunks de distância, reduzir resolução interna do CEF browser
- Ou usar textura estática (screenshot de baixa resolução) para monitores longe
- Prioridade: baixa — implementar só se a performance for realmente um problema

### 2.4 Gestão de Memória dos Browsers CEF
- **Atual:** browsers são criados e só destruídos quando o monitor é partido
- **Melhoria:** Destruir browsers de monitores que estão a mais de 12 chunks do jogador (LRU cache)
- Recriar quando o jogador se aproxima
- Usar `ClientTickEvent` para verificar distâncias periodicamente (a cada 20 ticks = 1s)

### 2.5 Evitar Alocação de Objetos no Render Loop
- **Atual:** `ByteBufferBuilder(256)` + `BufferBuilder()` criados a cada frame em cada BE renderizado
- **Mudança:** Reutilizar buffers estáticos ou usar pool de objetos
- Prioridade: baixa — micro-otimização, impacto mínimo

---

## Fase 3: Melhorias de UX

### 3.1 Botão "Refresh" no GUI
- Recarregar a página atual sem precisar de reescrever a URL

### 3.2 Barra de Progresso de Loading
- MCEF tem callbacks de loading state — mostrar "Loading..." na preview

### 3.3 Atalhos de Teclado no BrowserScreen
- `F5` = Refresh
- `Ctrl+L` = focar URL field
- `Ctrl+W` = fechar ecrã

### 3.4 Notificação quando Browser Crasha
- MCEF tem callback `onCrashed()` — mostrar mensagem ao jogador

---

## Fase 4: Funcionalidades Pendentes

### 4.1 Áudio real do CEF (mais complexo)
- **Estado atual:** data model + GUI + packet existem, mas sem captura/reprodução
- **Necessário:** Investigar API do MCEF para captura de áudio (`MCEFBrowser` has audio methods?)
- Se MCEF não suportar, usar abordagem alternativa: gravar sistema sound do PC
- Prioridade: baixa — muito complexo para o ganho

### 4.2 Botão "Fullscreen" no GUI
- Fechar o GUI e mostrar o browser a ocupar todo o ecrã (sobreposição)
- Requer nova Screen ou usar `MCEFBrowser.resize()` + input forwarding

---

## Ordem de Implementação

```
1. Fase 0 — Testar funcionalidade + verificar logs
2. Fase 1 — Sistema de Config (MonitorConfig.kt)
3. Fase 2.1 — Culling de monitores fora da vista
4. Fase 2.2 — Limite de monitores renderizados
5. Fase 2.4 — Gestão de memória (destruir browsers distantes)
6. Fase 3 — Melhorias de UX (refresh, loading, atalhos)
7. Fase 2.3 — LOD (se necessário)
8. Fase 4 — Funcionalidades pendentes (se relevante)
```
