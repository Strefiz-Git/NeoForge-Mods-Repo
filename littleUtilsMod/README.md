# LittleUtils — NeoForge 1.21.1 + Kotlin

Mod server-side de utilidades para o servidor LittleMine.

## Funcionalidades

### Durability Warning
Mostra um aviso na **action bar** (acima da hotbar) quando o item na mão está a ficar danificado:
- **Amarelo** — ≤50% da durabilidade
- **Vermelho** — ≤5% da durabilidade ou <25 usos restantes
- Só atualiza quando o estado muda (não spamma)

### Death Coords
Quando morres, envia no chat as coordenadas exatas e a dimensão.

### Fast Sleep
Quando a percentagem configurada de jogadores dormir (padrão: 50%), a noite salta automaticamente.

### /craft
Comando `/craft` que abre uma mesa de crafting 3×3 em qualquer lugar.

### AFK Detection
Marca jogadores como AFK após X minutos de inatividade (padrão: 5) e avisa no chat.

## Configuração

Atualmente as configurações são definidas no código (`ServerConfig.kt`). Os valores padrão são:

| Propriedade | Padrão |
|---|---|
| `durabilityWarning` | `true` |
| `durabilityYellowPercent` | `50` |
| `durabilityRedPercent` | `5` |
| `durabilityRedUses` | `25` |
| `deathCoords` | `true` |
| `fastSleep` | `true` |
| `fastSleepPercent` | `50.0` |
| `craftCommand` | `true` |
| `afkEnabled` | `true` |
| `afkTimeoutMinutes` | `5` |

## Compilar

```bash
./gradlew build
```

O JAR fica em `build/libs/littleutils-1.0.0.jar`.

## Dependências

- NeoForge 21.1.172
- Kotlin for Forge 5.11.0 (runtime: `kotlinforforge-5.11.0-all.jar`)
