# Damas no Café

Jogo de damas portuguesas para dois, em retrato, com tema de café. Um telemóvel
abre a mesa e serve o jogo por Wi-Fi; o outro entra com o número (ou a palavra)
da mesa. Não há servidores na nuvem nem contas: basta estarem na mesma rede.

## Jogar

1. **Telemóvel A** — *Abrir uma mesa*. Fica à espera e mostra o número (o último
   do IP), a palavra de café e o IP.
2. **Telemóvel B** — *Entrar numa mesa* → escreve `25` ou `panqueca` → *Entrar*.
3. Os dois tocam *Estou pronto*. As cores trocam a cada partida.

Sem Wi-Fi à mão, *Jogar cá em casa* faz duas pessoas no mesmo telemóvel.

## Regras de Portugal

- Tabuleiro 8×8, 12 peças por lado, só casas escuras.
- O peão anda e **come sempre para a frente**; só a dama come para trás.
- Captura obrigatória e lei da maioria: entre duas capturas vale a que come mais.
- A dama voa pela diagonal livre e pára em qualquer casa depois da peça saltada.
- Só vira dama quem termina a jogada na última linha do adversário.
- Ficar sem jogadas é perder; 50 jogadas de dama sem capturas é empate.

## Construir

Precisa de JDK 17 e do SDK do Android (API 36).

```sh
./gradlew :core:test          # motor de regras e mesas
./gradlew :app:assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O `app-debug.apk` publicado nas releases está assinado com a chave de debug, por
isso instala-se por sideload em qualquer telemóvel com Android 8 ou posterior.

## Organização

| Pasta | O que é |
| --- | --- |
| `core/` | Motor de regras, notação, estado das mesas e palavras de café (JVM puro, com testes) |
| `app/` | Interface Compose, servidor HTTP dentro do telemóvel e cliente de mesas |
| `tools/` | Gerador da lista de palavras das mesas |
| `webapp_old/` | A versão anterior em HTML/JS, guardada para consulta |

O servidor corre dentro da app enquanto a mesa está aberta, na porta 8742, e só
expõe a API do jogo (`/api/info`, `/api/open`, `/api/join`, `/api/poll`,
`/api/msg`, `/api/beat`) mais uma página de aviso.

Jogo de estimação para jogar com amigos — sem contas, sem telemetria, sem chaves.
