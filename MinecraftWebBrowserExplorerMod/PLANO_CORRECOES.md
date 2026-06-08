# Plano de Correções - MonitorMod (webbrowser)

## 1. Formação do Monitor (Multi-bloco)

### Regras
- [ ] Tem de ser um **quadrado ou retângulo** (medido em blocos)
- [ ] **Proporção máxima: 16:9**
- [ ] **Tamanho máximo: 320 blocos largura × 180 blocos altura**
- [ ] **Tamanho mínimo: 6 blocos largura × 3 blocos altura**
- [ ] **Cada bloco: 120 × 120 píxeis**
- [ ] Resolução total = `screenWidthBlocks * 120` × `screenHeightBlocks * 120`
- [ ] Monitores de um bloco só **não funcionam**
- [ ] `tryFormMonitor()` valida as dimensões antes de formar o ecrã

## 2. Renderização — Segmentos / Colunas Desalinhadas

### Problema
- [ ] Ao formar um monitor grande, a tela aparece dividida em segmentos (colunas no sítio errado)
- [ ] Píxeis parecem desalinhados entre os blocos adjacentes
- [ ] Possível causa: cálculo de UV por bloco está errado quando há múltiplos monitores lado a lado

### Por investigar
- Como o `MonitorBlockEntity` calcula `minU`/`minV`/`maxU`/`maxV` com base na posição relativa dentro do shape
- Se as coordenadas UV estão a usar a largura/altura total do shape corretamente

## 3. Item: MonitorCommand — Conexão a um Monitor

### Novo Comportamento
- [ ] **Comando desligado (padrão):** sem brilho, descrição: "Não conectado"
- [ ] **Comando ligado a um monitor:** brilho de encantamento, descrição "Conectado a [pos]"
- [ ] **Shift+left-click** num ecrã ativo (alcance normal do jogador) → comando fica conectado àquele monitor
- [ ] Se o ecrã já estiver ocupado (alguém a interagir) → "Monitor ocupado", não conecta
- [ ] Distância máxima para **usar** o comando conectado: **6 chunks (96 blocos) da borda mais próxima do monitor** até ao jogador

### Interação com Comando Conectado
- [ ] **Shift + Right-click** (qualquer distância ≤ 6 chunks) → abre `UrlScreen` para mudar URL
- [ ] **Left-click** (a olhar para o ecrã, ≤ 6 chunks) → liga controlos ao monitor (rato preso, inputs para CEF)
- [ ] **Left-click** (sem olhar para o ecrã ou fora de distância) → não faz nada (ou mostra "Fora de alcance" / "Olha para o ecrã")

### Conexão Perdida
- [ ] Se o monitor for partido ou desfeito (forma inválida) → browser morre, comando perde conexão
- [ ] Precisa de dar sync novamente (shift+left-click)

## 4. Input — Controlo do Monitor

### Problemas Atuais
- [ ] **Player ainda se move** quando tenta ligar inputs ao monitor
- [ ] **Teclado não é registado** no CEF
- [ ] **Rato fica visível** (deve ficar invisível/preso)
- [ ] **Não usa a crosshair** como posição do rato

### Comportamento Desejado
- [ ] Quando o jogador sincroniza (left-click com comando conectado):
  - **Player pode olhar à volta** (mover câmara) mas **não pode andar** (WASD cancelado)
  - **Rato invisível/preso**
  - **Crosshair do Minecraft (a "+" no centro do ecrã) é a posição do rato**
  - Onde o jogador olha determina onde está o rato no monitor
  - Se olhar para fora do ecrã → rato não mexe (fica na última posição ou borda)
  - Todos os inputs de teclado e rato vão para o CEF

## 5. Monitor Partido / Invalidação

- [ ] Se um bloco é partido → `invalidateMonitor()` é chamado
- [ ] Se a forma deixar de ser quadrado/retângulo → ecrã elimina-se
- [ ] Browser fechado, textura limpa
- [ ] Comando conectado perde ligação → "Conexão perdida" / descrição volta a "Não conectado"
- [ ] É preciso shift+left-click outra vez para reconectar

## 6. ESC

### Atual (confirmar se precisa de mudança)
- [ ] ESC liberta o rato mas browser continua ativo

## 6. Resolução

- **Cada bloco: 120 × 120 píxeis**
- Resolução total do browser = `screenWidthBlocks * 120` × `screenHeightBlocks * 120`
- Alterar `BrowserConstants`: `BLOCK_PIXEL_WIDTH = 120`, `BLOCK_PIXEL_HEIGHT = 120`

## 8. Distância de Renderização

- [ ] `getViewDistance() = 96` (6 chunks)
- [ ] `shouldRenderOffScreen() = true`

## 9. Debug Logging

- Já existe logging em todos os pontos principais

## 10. NBT do Comando (Sessões)

- [ ] Comando guarda `BlockPos` do monitor no NBT do item (`ItemStack`)
- [ ] Persiste ao sair/entrar no jogo
- [ ] Se monitor for partido → comando perde estado (NBT limpo, descrição atualizada, sem encantamento)
- [ ] Reconectar = shift+left-click outra vez

---

**Todas as perguntas respondidas.** Pronto para começar a implementar.

