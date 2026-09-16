# Arquitetura do EasyPace

## Visão geral em uma tela

```
   SENSORES                MOTOR (20 Hz)              SAÍDAS
   ---------               -------------              ------
   LocationSource  --.
   (GPS, 1 Hz)        \
                       >--> PaceEngine.tick() --> RunSnapshot --.
   MotionSource    --/       - fusão                            |
   (passos, ~3 Hz)           - filtro EMA                       +--> RunController (StateFlow)
                             - distância                        |         |
                             - máquina de estados               |         +--> CodecScreen (Compose)
                                                                |
                                                                +--> AudioCoach --> AlertPolicy --> Sfx
                                                                                                   (AudioTrack)
```

Tudo isso roda dentro de `RunService`, um *foreground service*, para sobreviver
com a tela apagada.

---

## Camadas e regra de dependência

| camada | pacote | conhece Android? | pode ser testada na JVM? |
|---|---|---|---|
| domínio | `core/` (menos `Prefs`) | **não** | sim |
| política de áudio | `audio/AlertPolicy`, `ToneSynth`, `CodecSounds` | **não** | sim |
| adaptadores | `sensors/`, `audio/Sfx`, `core/Prefs` | sim | não |
| orquestração | `service/` | sim | não |
| apresentação | `ui/` | sim (Compose) | previews |

A regra é simples: **nada em `core/` importa `android.*`**. É o que permite
simular uma corrida de 60 s em milissegundos nos testes.

---

## O loop de 20 Hz

`RunService.runLoop()` usa *deadline fixo* em vez de `delay(50)` puro:

```kotlin
var deadline = elapsedRealtime()
while (ativo) {
    deadline += Config.TICK_MS          // 50 ms
    ...trabalho...
    val sobra = deadline - elapsedRealtime()
    if (sobra > 0) delay(sobra) else deadline = elapsedRealtime()
}
```

Assim, se um tick atrasa, o próximo compensa e a média fica exatamente em
`TICK_HZ`. Se o sistema engasgar de vez, o loop reancora em vez de acumular
dívida e disparar vinte ticks seguidos.

Custo real: o tick faz umas poucas dezenas de operações de ponto flutuante —
o consumo de bateria é dominado pelo GPS, não pelo loop.

**Por que 20 Hz e não 1 Hz?** Porque o *estado* precisa reagir rápido, mesmo
que o GPS não. A cadência muda em ~300 ms; a 1 Hz você perderia até um segundo
inteiro só na amostragem, somado ao atraso do filtro.

---

## Fusão de sensores

```
gpsFresco    = agora - últimoFix   <= 1800 ms
cadenciaViva = agora - últimoPasso <= 2500 ms

velocidadeBruta =
    gpsFresco && cadenciaViva -> 0.65*GPS + 0.35*(passada * cadência/60)
    gpsFresco                 -> GPS
    cadenciaViva              -> passada * cadência/60
    nenhum                    -> null (decai suavemente para zero)
```

O **comprimento de passada** começa em 1,10 m e é aprendido em tempo real:
sempre que GPS e cadência estão bons ao mesmo tempo,

```
observado = velocidadeGPS / (cadência/60)
passada  += 0.05 * (observado - passada)
```

Esse aprendizado (`STRIDE_LEARN_ALPHA`) é o que faz o modo "só cadência"
funcionar quando você entra num túnel ou embaixo de árvores.

---

## Filtro e estabilidade

Três estágios, e cada um resolve um problema diferente.

**1. Mediana no GPS (5 leituras).** Contra picos de multipath, que são
outliers, não ruído gaussiano. Média não serve aqui: ela incorpora o pico.

**2. EMA com constante de tempo**, não alpha fixo:

```
alpha = 1 - e^(-dt/τ)
```

Isso deixa o filtro **independente da taxa do loop**: mude `TICK_HZ` de 20 para
30 e a resposta continua a mesma. Hoje `τ` é curto (2,5 s) — ele só emenda o
intervalo entre fixes de GPS; não é mais ele quem estabiliza a leitura.

**3. Janela deslizante (12 s)** — é aqui que a estabilidade acontece:

```
pace = (t_fim − t_início) / (distância_fim − distância_início) × 1000
```

O motivo é a hipérbole. `pace = 1000/v` tem derivada `−1000/v²`, então o mesmo
ruído de velocidade produz um erro de pace inversamente proporcional ao
quadrado da velocidade:

| velocidade | pace | erro de pace para ±0,1 m/s |
|---|---|---|
| 3,3 m/s | 5:00 /km | ±9 s/km |
| 2,2 m/s | 7:35 /km | ±21 s/km |
| 1,4 m/s | 11:54 /km | ±51 s/km |

Filtrar velocidade com mais força não resolveria sem destruir a resposta. A
janela resolve porque **integra**: 12 fixes médios valem muito mais que o
último fix sozinho, e o resultado é o pace médio de verdade daquele trecho.

Amostramos a janela a 5 Hz (não a 20 Hz) — 200 ms de resolução é de sobra e
evita guardar 240 pontos.

**4. Limitador de variação** (25 s/km por segundo) é só verniz visual, com uma
válvula de escape: diferenças acima de 90 s/km passam direto, senão sair de um
valor ruim levaria minutos.

---

## Máquina de estados

```
delta = paceAtual - meta        (positivo = mais LENTO)

            -tol        -tol+h        +tol-h        +tol
 ------------|-------------|-------------|------------|------------>
  RAPIDO_DEMAIS       <-- histerese -->            LENTO_DEMAIS
                          NO_PACE
```

Três proteções contra oscilação:

1. **banda morta** `±toleranceSecPerKm`;
2. **histerese** — sair exige `tol`, voltar exige `tol - hysteresis`;
3. **dwell** — o estado candidato precisa se manter por `minStateDwellMs`
   antes de virar oficial.

Mais `AQUECENDO` (primeiros `warmupMs`) e `PARADO` (abaixo de
`MIN_VALID_SPEED_MPS`), que nunca disparam som.

---

## Áudio

Nada de arquivos `.wav`: os bipes são calculados por `ToneSynth` como PCM
16 bits mono a 44,1 kHz e carregados uma única vez em `AudioTrack` no modo
`MODE_STATIC`. Tocar é `stop() → reloadStaticData() → play()`, com latência de
poucos milissegundos.

- **timbre `CODEC`** = senoide + 35% do 3º harmônico + 15% do 5º. É o "buzz"
  eletrônico que corta o ruído da rua sem ser estridente.
- **envelope trapezoidal** (ataque 5 ms, decaimento 20 ms) evita o "clique" que
  aparece quando um buffer começa ou termina fora do zero.
- `USAGE_ASSISTANCE_SONIFICATION` faz o bipe tocar **por cima** da sua música,
  sem pausá-la.

A política de repetição vive em `AlertPolicy` — separada dos sons, e testada.

---

## Estado compartilhado

`RunController` é um `object` (singleton) com `StateFlow`s. O serviço escreve,
a Activity lê com `collectAsStateWithLifecycle()`. Consequência prática: você
pode girar a tela, sair do app e voltar — a corrida não é afetada, porque nada
importante mora na Activity.

---

## Como estender

**Adicionar um som novo** (ex.: aviso a cada km)
1. `CodecSounds`: escreva a função que monta o buffer.
2. `Cue`: acrescente o valor no enum.
3. `AlertPolicy.decide`: diga quando ele deve disparar.
4. `AudioCoach`: crie o `Sfx` e trate o novo `Cue` em `play()`.

**Adicionar um dado na tela** (ex.: frequência cardíaca)
1. `RunSnapshot`: novo campo.
2. `PaceEngine.tick`: preencha.
3. `CodecScreen`: mostre.

**Gravar o histórico das corridas**
Crie `data/RunRepository.kt`, assine `RunController.snapshot` dentro do serviço
e grave num CSV em `filesDir` a cada segundo. O `core/` não precisa mudar.

**Trocar a estética**
`CodecColors` e `CodecTypography` concentram toda a identidade visual. As peças
(`CodecPanel`, `cornerBrackets`, `scanlines`) são genéricas e reutilizáveis no
próximo app.
