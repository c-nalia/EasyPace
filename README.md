# EasyPace — treinador de pace com estética Codec

Aplicativo Android pessoal que ouve os sensores do celular durante a corrida e
avisa **pelo fone**, sem você precisar olhar a tela:

| situação | som | quando |
|---|---|---|
| pace **acima** da meta (você acelerou) | bipe **agudo** duplo, subindo | ao entrar no estado e a cada 4 s |
| pace **estável** dentro da tolerância | **chamada de codec** (dois toques, duas vezes) | ao entrar no estado e a cada **10 s** |
| pace **abaixo** da meta (você caiu de ritmo) | bipe **grave** descendo | ao entrar no estado e a cada 4 s |

> O mapeamento agudo/grave pode ser invertido em **AJUSTES → inverter agudo/grave**,
> sem recompilar.

---

## Como gerar o APK

Pré-requisito: **Android Studio instalado** (ele já traz o JDK e o SDK).
Abra o Prompt de Comando nesta pasta.

**Você NÃO precisa de depuração USB.** Ela só serve para o comando `install`.

Na primeira vez, duas linhas:

```
build.bat keystore     cria a sua chave de assinatura (pede uma senha)
build.bat apk          gera o APK final assinado em dist\
```

Se aparecer `[X] Nenhum JDK compatível encontrado`, rode antes:

```
build.bat jdk          baixa um JDK 21 para tools\jdk21 (~190 MB, uma vez)
```

Isso acontece quando o Java que vem dentro do Android Studio é mais novo que
o **Java 23** — o Gradle 8.11 e o plugin Android 8.7 usados aqui não
reconhecem versões acima disso, e o build morre com uma mensagem que é só o
número da versão (por exemplo `25.0.2`). O `build.bat jdk` resolve instalando
um JDK 21 dentro da pasta do projeto, sem tocar no Android Studio nem no Java
do sistema.

Nas próximas versões:

```
build.bat bump         soma 1 no versionCode
build.bat apk          gera o novo APK
```

Outros comandos:

```
build.bat              APK de teste (debug), sem precisar de chave
build.bat test         roda os testes automatizados
build.bat lint         análise estática do Android
build.bat clean        limpa
build.bat install      instala pelo cabo (esse sim precisa de depuração USB)
build.bat help         ajuda
```

O `build.bat` acha sozinho o JDK do Android Studio e o Android SDK, escreve o
`local.properties`, baixa o `gradle-wrapper.jar` se faltar, e copia o APK
pronto para `dist\` com a versão e a data no nome.

### Instalando no celular sem cabo de depuração

1. Leve o `.apk` de `dist\` para o celular — cabo USB no modo "Transferência
   de arquivos", Google Drive, WhatsApp para você mesmo, e-mail, tanto faz.
2. No celular, abra o arquivo (em Downloads ou Meus Arquivos).
3. O Android avisa que a fonte é desconhecida: toque em **Configurações** e
   permita a instalação para o app que abriu o APK (Arquivos, Chrome,
   WhatsApp...). Isso é por app, e você faz uma vez só.
4. Toque em **Instalar**. Se o Play Protect reclamar, escolha **Instalar mesmo
   assim** — o aviso aparece só porque o app não veio da Play Store.

### Sobre a chave de assinatura

> **Nunca versione a chave.** `easypace.jks` e `keystore.properties` estão no
> `.gitignore`, e é assim que tem de ficar. A chave de assinatura *é* a
> identidade do app: quem tiver ela pode publicar atualizações que o Android
> aceita como se fossem suas. Se ela vazar num commit, apagar o arquivo depois
> não resolve — o conteúdo continua acessível no histórico, e o certo é gerar
> uma chave nova.


`build.bat keystore` cria `easypace.jks` na pasta do projeto, válido por 27
anos. **Guarde esse arquivo e a senha.** É ele que permite *atualizar* o app
instalado: se você perder a chave, as versões futuras só entram desinstalando
a antiga primeiro. Ele já está no `.gitignore` — nunca suba essa chave para
lugar nenhum.

O APK de `debug` (gerado por `build.bat` sem argumento) também instala, mas
usa a chave automática do Android Studio, tem o app marcado como depurável e
instala com o nome de pacote `br.easypace.codec.debug`. Serve para testar
rápido; para o dia a dia, prefira `build.bat apk`.

---

## Como usar

1. Abra o app e toque em **CONCEDER PERMISSÕES** (localização, atividade física
   e notificações).
2. Ajuste a meta com os botões `-10 / -5 / +5 / +10` (segundos por km).
3. Toque em **INICIAR**. O app fica ~8 s calibrando ("CALIBRANDO SENSORES") e
   depois começa a avisar.
4. Pode apagar a tela e guardar o celular: o monitor roda num *foreground
   service* com notificação persistente, e a notificação tem um botão **PARAR**.

---

## Como ele mede o pace

O GPS entrega **1 leitura por segundo** — sozinho, ele avisaria tarde demais.
Por isso o EasyPace funde duas fontes:

- **GPS** (`FusedLocationProvider`): dá a velocidade verdadeira.
- **Cadência** (sensor de passos, ou picos do acelerômetro): reage em ~300 ms.

Enquanto você corre, o app compara as duas e **aprende o seu comprimento de
passada**. Entre um fix de GPS e o próximo, a cadência preenche o intervalo.
O motor recalcula tudo **20 vezes por segundo** (`Config.TICK_HZ`).

### Por que o número fica parado

Mostrar `1000 / velocidade agora` não funciona. Pace é uma hipérbole: a
derivada é `-1000/v²`, então o mesmo errinho de velocidade vira um erro de
pace muito maior quanto mais devagar você vai. Correndo a 3,3 m/s, um erro de
0,1 m/s desloca o pace em 9 s/km; **andando** a 1,4 m/s, o mesmo erro desloca
**51 s/km**. O ruído não aumenta — a amplificação é que é maior.

Duas defesas:

1. **Mediana dos últimos 5 fixes de GPS.** Perto de prédios o GPS solta picos
   isolados que chegam a dobrar a velocidade. Uma média absorve o pico e passa
   a mentir; a mediana o descarta. Sem isso, com picos a cada 20 s, o app
   reporta você ~8 s/km mais *rápido* do que está — erro sistemático, não
   tremor.
2. **Janela deslizante de 12 s.** O pace é a distância percorrida na janela
   dividida pelo tempo dela, não a velocidade instantânea. Integrar mata o
   ruído.

Medido em simulação (corrida a 5:00/km, GPS com ruído e picos):

| | desvio do pace | erro médio |
|---|---|---|
| sem mediana, sem janela | 15,2 s/km | −8,4 s/km |
| só mediana | 8,0 s/km | −1,0 s/km |
| só janela | 9,2 s/km | −13,9 s/km |
| **as duas (atual)** | **5,4 s/km** | **−1,0 s/km** |

Andando a 12:00/km, o desvio cai de 53 s/km para 24 s/km.

O custo é atraso: a janela de 12 s reconhece uma mudança real de ritmo em ~9 s
(com 8 s seriam 7 s; com 20 s, 12 s). Ajustável em **AJUSTES → janela do
pace**, sem recompilar.

A tela também é redesenhada só 4x por segundo, embora o motor continue em
20 Hz — um dígito trocando 20 vezes por segundo parece instável mesmo quando a
medição está boa.

Para não ficar alternando alerta/meta na fronteira, há três amortecedores:

1. **banda morta** (`toleranceSecPerKm`, padrão ±8 s/km);
2. **histerese** (`hysteresisSecPerKm`): depois de sair da faixa é preciso
   voltar mais para dentro do que o ponto de saída;
3. **tempo mínimo de confirmação** (`minStateDwellMs`, padrão 2,5 s).

---

## Onde mexer

| quero mudar... | arquivo |
|---|---|
| tolerância, **janela do pace**, filtros, prazos dos bipes | `core/Config.kt` |
| o desenho dos sons (frequências, duração, timbre) | `audio/CodecSounds.kt` |
| quando cada som toca | `audio/AlertPolicy.kt` |
| a lógica de pace e a máquina de estados | `core/PaceEngine.kt` |
| cores e fontes | `ui/theme/CodecTheme.kt` |
| o layout da tela | `ui/CodecScreen.kt` |
| molduras, scanlines, botões | `ui/components/CodecUi.kt` |
| ícone | `res/drawable/ic_launcher_foreground.xml` |

Detalhes em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

---

## Testes

`build.bat test` roda testes de JVM (sem celular) que simulam uma corrida
inteira com relógio falso: reconhecimento do ritmo, alertas de acelerar e
desacelerar, histerese, integração de distância, cadência sem GPS, e a política
de repetição dos sons.

---

## Publicando este repositório

O que o Git envia são os **47 arquivos de fonte** — cerca de 280 KB no total.
Fica de fora, por `.gitignore`:

| não vai | por quê |
|---|---|
| `easypace.jks`, `keystore.properties` | a chave de assinatura e a senha dela |
| `local.properties` | tem o caminho do SDK com o seu nome de usuário |
| `dist/`, `build/`, `app/build/` | APKs e artefatos gerados |
| `tools/` | o JDK de ~330 MB baixado pelo `build.bat` |
| `.gradle/`, `.kotlin/`, `.idea/` | caches de ferramenta e config de IDE |

Quem clonar o repositório consegue compilar: rode `build.bat`, que recria o
`local.properties` sozinho a partir do SDK da máquina dele. Para gerar um APK
assinado, cada pessoa cria a **própria** chave com `build.bat keystore` — o
`keystore.properties.example` documenta o formato.

O `.gitattributes` fixa os finais de linha: `.bat` sempre CRLF (senão o
`cmd.exe` engasga) e `gradlew` sempre LF (senão não roda em Linux/macOS).
Sem isso, um clone em outra máquina pode quebrar os scripts sem motivo
aparente.

---

## Licença

MIT — veja [LICENSE](LICENSE). Troque `SEU_USUARIO_GITHUB` no arquivo pelo seu
usuário antes do primeiro push.

---

## Limitações conhecidas

- Precisa de **céu aberto** para o GPS. Em esteira/indoor o app cai no modo
  cadência, que depende do comprimento de passada aprendido — calibre uma vez
  correndo na rua antes.
- Alguns fabricantes (Xiaomi, Samsung, Motorola) matam serviços em segundo
  plano. Se o app parar sozinho, tire o EasyPace da otimização de bateria.
- O APK de `release` só é assinado se existir um `keystore.properties`
  (criado por `build.bat keystore`). Sem ele, o Android recusa a instalação.
