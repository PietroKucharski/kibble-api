# Roteiro — Projeto 1: Mini-ERP de Custo de Produção Pet

Oct 29, 2026 · @Pietro

## Visão geral

O objetivo é construir, em cerca de 10 a 12 semanas, um sistema para uma fábrica de ração e petiscos que calcula o custo de produção a partir da fórmula do produto e das etapas do processo. O domínio é o mesmo do portal da Madepar (estrutura, roteiro, custo, orçamento), trocando portas e kits por ração e petiscos, e feito "do jeito certo", com banco próprio, segurança real e testes.

**Como a fábrica funciona (o que o sistema modela):** a ração é produzida em bateladas, por exemplo de 1.000 kg. A fórmula diz quanto de cada ingrediente entra (milho, farelo de soja, farinha de carne, gordura de frango, premix vitamínico). A massa passa por moagem, mistura, extrusão, secagem, recobrimento com gordura e palatabilizante, resfriamento e ensacamento. Na secagem a massa perde umidade, então 1.000 kg de ingredientes rendem menos de 1.000 kg de ração. O produto vendido é o SKU embalado, como "Ração Cão Adulto 15 kg", que usa a ração a granel mais o saco e a etiqueta.

**Escopo do MVP (o que precisa existir para considerar pronto):**

- Cadastro de ingredientes com custo por kg e composição nutricional a partir de um catálogo de nutrientes (umidade, proteína bruta, extrato etéreo, matéria fibrosa, matéria mineral, cálcio e fósforo, em %).
- Cadastro de embalagens (sacos, potes, etiquetas) com custo unitário.
- Fórmulas: premix produzido na fábrica e ração ou petisco a granel, com a lista de ingredientes (ou premix) em percentual, tamanho da batelada, umidade final e status (rascunho ou ativa).
- SKUs: produto embalado que combina a ração a granel com a embalagem (estrutura multinível).
- Etapas de processo (moagem, mistura, extrusão, secagem e outras) com equipamento, custo por hora e capacidade em kg/hora.
- Cadastro de clientes: pet shops, agropecuárias e distribuidores.
- Orçamentos com itens, cálculo automático de custo (ingredientes + processo + embalagem), custo por kg, markup e preço de venda (sem impostos).
- Login com perfis: ADMIN, FORMULAÇÃO (ingredientes, fórmulas e processo) e VENDAS (orçamentos).

**Fora do MVP (deixe para depois):** controle de lotes com validade e rastreabilidade, histórico de preços dos ingredientes, otimização da fórmula a custo mínimo, versões de fórmula (o custo cotado já protege os orçamentos enviados), rotas de processo compartilhadas entre produtos (no MVP, cada produto tem as próprias etapas), impostos sobre a venda (ICMS, PIS e COFINS), rateio de custos indiretos, relatórios em PDF, integração com ERP (isso é o Projeto 2).

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 4, Spring Web, Spring Data JPA, Spring Security, Bean Validation |
| Arquitetura | Monólito modular com Spring Modulith (limites entre módulos verificados por teste e eventos com outbox) |
| Banco | PostgreSQL, migrações com Flyway |
| Documentação da API | springdoc-openapi 3.x com Scalar (e Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers, MockMvc |
| Front-end | Next.js, TypeScript, Tailwind, shadcn/ui, react-hook-form + zod, TanStack Query |
| Infra | Docker, Docker Compose, GitHub Actions |

**O que este projeto corrige em relação ao madepar:**

- Segredos em variáveis de ambiente, nunca no código.
- Token JWT assinado em vez de cookie JSON forjável.
- Todas as rotas exigem autenticação e checam o perfil.
- Validação de entrada no servidor, paginação no banco e tratamento de erro padronizado.
- Testes automatizados desde a primeira fase.

## Fase 0 — Preparação e base (semana 1)

Antes de codar o domínio, deixe o ambiente pronto e revise o essencial de Java e Spring, já que seu dia a dia foi em TypeScript.

**Ambiente:**

- [ ] Instalar JDK 21 (Temurin ou via SDKMAN), Maven ou Gradle, IntelliJ IDEA Community, Docker Desktop e um cliente SQL (DBeaver).
- [ ] Criar o repositório no GitHub com dois diretórios: `backend/` e `frontend/` (monorepo simples).
- [ ] Gerar o projeto no start.spring.io com Maven, Spring Boot 4.1.x, Java 21, empacotamento Jar e configuração YAML, group io.github.pietrokucharski e as dependências: Web, Data JPA, Validation, Security, PostgreSQL Driver, Flyway, Lombok, Actuator, Testcontainers e DevTools.
- [ ] Criar o `docker-compose.yml` com PostgreSQL e subir o banco.
- [ ] Configurar `application.yml` lendo URL, usuário e senha do banco de variáveis de ambiente, com um `.env.example` no repositório.
- [ ] Fazer um endpoint `GET /api/health` funcionar.

**Estudo rápido (se algo estiver enferrujado):**

- [ ] Java moderno: records, `Optional`, streams, `var`, `BigDecimal` para dinheiro.
- [ ] Spring: injeção de dependência, `@RestController`, `@Service`, `@Repository`, ciclo de uma requisição. Muitos tutoriais ainda são do Spring Boot 3; se um exemplo não compilar, confira o guia de migração para o Boot 4.
- [ ] JPA: entidade, relacionamentos (`@ManyToOne`, `@OneToMany`), lazy loading e o problema N+1.

* [ ] Monólito modular: o que é um módulo, fachada pública, referência por ID e eventos entre módulos (documentação do Spring Modulith).

**Estrutura de pacotes sugerida (por domínio, não por camada):**

```
com.seunome.petcusto
├── shared/        (erros, paginação, config)
├── auth/          (usuários, login, JWT)
├── ingredient/    (ingredientes e catálogo de nutrientes)
├── packaging/     (embalagens)
├── formula/       (premixes, fórmulas, SKUs e composição nutricional)
├── process/       (equipamentos e etapas)
├── costing/       (cálculo de custo)
├── customer/      (clientes)
└── budget/        (orçamentos)
```

Dentro de cada módulo, organize por **agregado** (um conceito de negócio por pasta), e não por camada (`entity/`, `service/`, `controller/`). A camada já aparece no sufixo do nome da classe. Na raiz do módulo ficam só a fachada e os DTOs, que são a API pública; os subpacotes são internos. Exemplo do módulo `formula`:

```
formula/
├── FormulaFacade.java          ← API pública do módulo (só delega)
├── dto/                        ← records que outros módulos podem usar
├── product/                    ← agregado: produto, itens da fórmula e garantias
│   ├── Product.java
│   ├── FormulaItem.java
│   ├── NutritionalGuarantee.java
│   ├── ProductRepository.java
│   ├── ProductService.java
│   └── ProductController.java
└── nutrition/
    └── NutritionCalculator.java
```

**Regras entre módulos.** As setas mostram quem pode usar quem. Nenhuma seta pode voltar: quando um módulo de baixo precisa avisar um de cima, ele publica um evento.

```
ingredient ◄── formula ◄── process
packaging  ◄──┘   ▲          ▲
    ▲             │          │
    └──────── costing ───────┘
                  ▲
customer ◄──── budget ────► auth

eventos: ingredient, packaging ···► formula, budget
```

1. **API pública mínima:** outro módulo só usa a fachada e os DTOs. Entidades, repositórios e services ficam nos subpacotes internos. O Spring Modulith verifica isso num teste (Fase 2).
2. **Referência entre módulos só por ID:** sem `@ManyToOne` para entidade de outro módulo e sem chave estrangeira entre tabelas de módulos diferentes. A existência do ID é validada pela fachada do outro módulo.
3. **Cada tabela tem um único dono**, e só ele grava nela (Fase 1).
4. **Consulta pela fachada, efeito colateral por evento:** ler dados de outro módulo é uma chamada à fachada; mudar algo em outro módulo é sempre um evento, publicado com outbox e tratado por um consumidor idempotente.
5. **Uma transação nunca grava em dois módulos.**
6. **O `shared` é pequeno e sem regra de negócio:** erros, paginação e utilitários de decimal. Enums do domínio ficam no módulo dono.

**Quanto modelar em cada módulo:**

| Módulos | Tipo | Intensidade |
| --- | --- | --- |
| `formula`, `costing` | Core | Rica: value objects (`Percentage`, `Money`, `Kilograms`) e regras dentro da entidade, como `product.activate()` |
| `ingredient`, `packaging`, `process`, `budget` | Supporting | Moderada: máquina de estados do orçamento no agregado, o resto em services simples |
| `auth`, `customer` | Generic | Mínima: CRUD direto, sem forçar riqueza onde não há regra |

## Fase 1 — Modelagem do domínio e banco (semana 2)

A modelagem é a parte mais importante: se a estrutura do produto estiver certa, o cálculo de custo sai quase sozinho. Desenhe antes de codar.

| Tabela | Módulo dono | Campos principais | Observação |
| --- | --- | --- | --- |
| `users` | auth | id, nome, email, senha\_hash, perfil, ativo | Senha com BCrypt; perfil: ADMIN, FORMULACAO ou VENDAS |
| `refresh_tokens` | auth | id, usuario\_id, token\_hash, familia\_id, expira\_em, revogado\_em, substituido\_por | Só o hash SHA-256 do token; a família permite revogar todos os tokens de uma sessão |
| `nutrients` | ingredient | id, codigo, nome, unidade | Sem `ativo`: o catálogo vem da migração e não tem exclusão. Catálogo único: umidade, proteína bruta, extrato etéreo, matéria fibrosa, matéria mineral, cálcio, fósforo |
| `ingredients` | ingredient | id, codigo, descricao, custo\_kg, ativo | Custo de aquisição em `NUMERIC(15,4)` |
| `ingredient_nutrients` | ingredient | id, ingrediente\_id, nutriente\_id, valor\_pct | Composição do ingrediente, um nutriente por linha |
| `packaging` | packaging | id, codigo, descricao, tipo, capacidade\_kg, custo\_unitario, ativo | Saco, pote, etiqueta |
| `products` | formula | id, codigo, descricao, especie, tipo, categoria, tamanho\_batelada\_kg, umidade\_final\_pct, status\_formula, peso\_liquido\_kg, tamanho\_lote\_envase, ativo | Tipo: PREMIX, GRANEL ou SKU; categoria: RACAO ou PETISCO; status\_formula: RASCUNHO ou ATIVA. `status_formula` diz se a fórmula é válida e `ativo` é a exclusão lógica: são colunas independentes. Batelada, umidade final e status valem para premix e granel; peso líquido e lote de envase (em unidades), para SKU |
| `formula_items` | formula | id, produto\_id, ingrediente\_id ou premix\_id, percentual | Composição do premix ou do granel; soma = 100% para ativar; cada item é um ingrediente ou um premix |
| `product_components` | formula | id, produto\_pai\_id, produto\_filho\_id ou embalagem\_id, quantidade | SKU = um granel + embalagens |
| `nutritional_guarantees` | formula | id, produto\_id, nutriente\_id, minimo\_pct, maximo\_pct | Níveis de garantia do rótulo, ligados ao catálogo de nutrientes |
| `equipment` | process | id, codigo, descricao, custo\_hora, capacidade\_kg\_hora, ativo | Moinho, misturador, extrusora, secador, ensacadeira. `custo_hora` é o custo total da hora: máquina, energia e operador |
| `process_steps` | process | id, produto\_id, equipamento\_id, sequencia, perda\_pct, tempo\_setup\_min | Etapas e perdas do premix, do granel e o ensacamento do SKU |
| `customers` | customer | id, nome, documento, tipo, email, telefone, ativo | Pet shop, agropecuária ou distribuidor |
| `budgets` | budget | id, numero, cliente\_id, status, markup\_pct, validade, desatualizado, versao, criado\_por, criado\_em | Status: RASCUNHO, ENVIADO, APROVADO, RECUSADO; `numero` gerado por sequence do banco; `versao` para lock otimista; `desatualizado` marca custo calculado com dados antigos |
| `budget_items` | budget | id, orcamento\_id, produto\_id, quantidade\_unidades, custo\_ingredientes, custo\_processo, custo\_embalagem, custo\_unitario, custo\_kg, preco\_venda, detalhamento\_custo, invalido | Custo cotado, gravado no momento do cálculo; `detalhamento_custo` em JSONB com o custo por ingrediente, etapa e embalagem; quantidade em unidades do SKU; preço sem impostos |

**Referências entre módulos (sem chave estrangeira, validadas pela fachada do módulo dono):** `formula_items.ingrediente_id` e `nutritional_guarantees.nutriente_id` → ingredient; `product_components.embalagem_id` → packaging; `process_steps.produto_id` → formula; `budgets.cliente_id` → customer; `budgets.criado_por` → auth; `budget_items.produto_id` → formula. Todas as outras referências ficam dentro do mesmo módulo e têm chave estrangeira normal.

- [ ] Desenhar o diagrama ER (dbdiagram.io ou draw.io) e salvar a imagem no repositório, em `docs/`, agrupando as tabelas por módulo dono.
- [ ] Criar as migrações Flyway nomeadas por módulo: `V1__auth_create_users.sql`, `V2__ingredient_create_ingredients.sql` e assim por diante. O catálogo de nutrientes entra numa migração (e não no seed), porque o sistema depende dele.
- [ ] Adicionar restrições no banco: `CHECK (quantidade > 0)`, `UNIQUE (codigo)`, chaves estrangeiras **só dentro do mesmo módulo**, um `CHECK` garantindo que o componente tem exatamente um filho e percentual entre 0 e 100, e outro garantindo que cada item da fórmula tem exatamente um entre ingrediente e premix.
- [ ] Criar uma migração de dados de exemplo (`V99__seed.sql` ou um perfil `dev`) com um premix vitamínico produzido na fábrica, uma ração de cão adulto (batelada de 1.000 kg, umidade final de 10% e uns 8 ingredientes, incluindo o premix), um petisco e SKUs de 1 kg e 15 kg (com lote de envase de 2.000 sacos).
- [ ] Mapear as entidades JPA, com referências a outros módulos como campos de ID (sem `@ManyToOne` entre módulos), e conferir que a aplicação sobe com `spring.jpa.hibernate.ddl-auto=validate`.

**Pontos de atenção:** use `BigDecimal` para todo valor monetário e percentual, nunca `double`. Guarde no item do orçamento o custo calculado, para que mudar o preço do milho amanhã não altere um orçamento já enviado. Trabalhe sempre em kg no banco e converta para gramas ou toneladas só na tela. Perdas e percentuais ficam em % no banco e são convertidos para fração num único lugar (o value object Percentage). Cadastros e fórmulas guardam atualizado\_por e atualizado\_em, para saber quem mudou custos e fórmulas.

## Fase 2 — API de cadastros (semanas 3 e 4)

Comece pelo cadastro mais simples (ingredientes) e use-o como molde para os outros. Só avance quando o primeiro estiver completo, com validação, paginação, erro padronizado e teste.

**Base compartilhada (faça uma vez):**

- [ ] `SecurityFilterChain` mínima desde já: `/api/health` liberado e todo o resto exigindo autenticação (HTTP Basic com um usuário de desenvolvimento lido de variáveis de ambiente, só no perfil `dev`). Assim todo endpoint nasce protegido; a Fase 3 troca isso pelo JWT.
- [ ] `GlobalExceptionHandler` com `@RestControllerAdvice`, retornando erros no formato RFC 7807 (`ProblemDetail`) para 400, 404, 409 e 500.
- [ ] Exceções de domínio: `NotFoundException`, `BusinessException`.
- [ ] Resposta paginada padrão usando `Pageable` do Spring, com limite máximo de `size` (por exemplo, 100) e uma lista de campos permitidos no `sort` por recurso (400 fora dela, em vez de erro 500).
- [ ] `DELETE` faz exclusão lógica (`ativo = false`) e devolve 204, com o mesmo padrão em todos os cadastros.
- [ ] DTOs como `record` separados das entidades: `CreateXRequest`, `UpdateXRequest`, `XResponse`. Mapeamento manual ou com MapStruct.
- [ ] springdoc-openapi 3.x (a linha compatível com o Spring Boot 4) e o Scalar (com.scalar.maven:scalar-webmvc) configurados; o Scalar fica em /scalar e o Swagger em `/swagger-ui.html`.
- [ ] Uma fachada pública por módulo (`IngredientFacade`, `FormulaFacade` e assim por diante), com DTOs em `record` e buscas em lote (`findAllByIds`). Outros módulos só usam a fachada.
- [ ] Spring Modulith (confira a versão compatível com o Spring Boot 4): o teste `ApplicationModules.of(Application.class).verify()`, ligado logo depois do primeiro cadastro, que falha se houver ciclo entre módulos ou acesso ao pacote interno de outro módulo; e o registro de publicação de eventos (outbox), com a tabela no banco, usado a partir do primeiro evento.

**Cadastros, na ordem:**

1. Nutrientes e ingredientes: catálogo de nutrientes só para leitura (vem da migração) e CRUD de ingredientes com a composição por nutriente, busca por código ou descrição, filtro por ativo. A mudança de custo tem endpoint próprio, `PATCH /api/ingredients/{id}/cost`, que é onde nasce o evento de custo alterado.
2. Embalagens: CRUD simples, com `PATCH /api/packaging/{id}/cost` para o custo.
3. Clientes: CRUD, validação de CPF/CNPJ com uma anotação customizada (`@CpfCnpj`).
4. Equipamentos: CRUD com custo por hora (máquina, energia e operador) e capacidade.
5. Premixes, produtos a granel e fórmulas: CRUD do produto (cadastre primeiro o premix e depois o granel que o usa) e endpoints aninhados, como `PUT /api/products/{id}/formula` (substitui a fórmula inteira; cada item é um ingrediente ou um premix; o rascunho aceita qualquer soma), `GET /api/products/{id}/nutrition`, que calcula a composição nutricional da fórmula, e `POST /api/products/{id}/activate`, que exige soma de 100% e garantias atendidas.
6. SKUs: `POST /api/products/{id}/components` ligando o granel e as embalagens, e `GET /api/products/{id}/structure` devolvendo a árvore.
7. Processo e garantias: `PUT /api/products/{id}/steps` e `PUT /api/products/{id}/guarantees`, cada um substituindo a lista inteira (a ordem das etapas é a ordem da lista).

**Regras de negócio para implementar e testar:**

- [ ] A soma dos percentuais precisa dar exatamente 100% (tolerância de 0,01) para ativar a fórmula. O rascunho pode ser salvo com qualquer soma, para o editor funcionar ao vivo.
- [ ] A composição calculada na umidade final do produto precisa atender os níveis de garantia (por exemplo, proteína bruta mínima de 22%). O resultado fica em `status_formula`: se não atender, a fórmula continua RASCUNHO e o produto não pode entrar em orçamento.
- [ ] Editar a fórmula, as garantias ou os componentes de um produto ATIVO volta ele para RASCUNHO e publica `formula.product.changed`.
- [ ] Estrutura do SKU: exatamente um granel e ao menos uma embalagem. Premix e granel não têm componentes. A capacidade da embalagem precisa ser maior ou igual ao peso líquido do SKU.
- [ ] Não permitir ciclo na estrutura: um produto não pode, direta ou indiretamente, conter a si mesmo. Exemplo real: um premix não pode conter o granel que usa esse premix.
- [ ] Ao gravar uma referência a outro módulo (ingrediente na fórmula, nutriente na garantia, embalagem no SKU), validar pela fachada do módulo dono que o ID existe e está ativo (no caso do nutriente, só que existe, porque o catálogo não tem exclusão), já que não há chave estrangeira entre módulos.
- [ ] Desativar ingrediente ou embalagem é sempre permitido. O módulo publica `ingredient.ingredient.deactivated` (ou `packaging.packaging.deactivated`), e o `formula` volta para RASCUNHO as fórmulas e SKUs que usam o item, o que os tira de orçamentos novos. Já um produto usado em outra fórmula ou SKU continua bloqueado (409), porque essa verificação acontece dentro do próprio `formula`.
- [ ] Código único por cadastro e exclusão lógica (`ativo = false`).

**Composição nutricional na umidade final:** a média ponderada dos ingredientes dá a composição da massa antes de secar. Como a secagem tira água, os nutrientes ficam mais concentrados no produto final. Calcule em matéria seca e converta para a umidade final do produto (`umidade_final_pct`), considerando que a perda da secagem é só água:

```latex
N_{final} = \frac{N_{massa}}{100 - U_{massa}} \times (100 - U_{final})
```

Exemplo: uma massa com 20% de proteína e 24% de umidade, seca até 10% de umidade, termina com 20 / 76 × 90 ≈ 23,7% de proteína. Sem esse ajuste, o sistema recusaria fórmulas que atendem o rótulo. O premix tem a composição calculada pela mesma regra e entra no granel como se fosse um ingrediente.

**Aprendizado extra desta fase:** use `@EntityGraph` ou `JOIN FETCH` na consulta da estrutura e ative o log de SQL para ver o problema N+1 acontecer e desaparecer. Entre módulos vale o mesmo cuidado: busque em lote pela fachada (findAllByIds), e não um ID por vez.

## Fase 3 — Segurança e autenticação (semana 5)

Esta fase resolve diretamente os quatro problemas mais graves do madepar: segredo no código, sessão forjável, rotas abertas e falta de controle por perfil.

- [ ] Cadastro de usuários restrito ao ADMIN; senha guardada com `BCryptPasswordEncoder`. O primeiro ADMIN é criado na inicialização a partir de variáveis de ambiente (`ADMIN_EMAIL`, `ADMIN_PASSWORD`), sem senha fixa no seed.
- [ ] `POST /api/auth/login` validando email e senha e devolvendo um access token JWT (cerca de 15 minutos) e um refresh token opaco (cerca de 7 dias). Em caso de erro, mensagem genérica: "email ou senha inválidos".
- [ ] Refresh token guardado só como hash SHA-256 (tabela `refresh_tokens`) e girado a cada uso: o refresh devolve um token novo e revoga o antigo. Se um token já usado aparecer de novo, toda a família daquela sessão é revogada.
- [ ] `POST /api/auth/refresh` e `POST /api/auth/logout` (que revoga a família). O access token continua válido até expirar, o que é aceitável com 15 minutos.
- [ ] JWT com o resource server nativo do Spring (Nimbus), HS256, chave lida de `JWT_SECRET` (pelo menos 256 bits, nunca no código), validando `exp` e `iss`.
- [ ] `SecurityFilterChain` stateless no lugar da mínima da Fase 2: tudo em `/api/**` exige autenticação, exceto login, refresh e health.
- [ ] Autorização por perfil com `@PreAuthorize`, seguindo a matriz abaixo. Perfis: ADMIN, FORMULACAO e VENDAS.
- [ ] Pegar o usuário logado do token, pela API pública do auth (CurrentUser), e nunca de um campo enviado pelo front, para preencher `criado_por`.
- [ ] CORS configurado só para a origem do front.
- [ ] Rate limit no login com Bucket4j, por IP e por email. Em memória serve para uma instância só.
- [ ] Em produção, expor só `/actuator/health`, e deixar o Scalar e o Swagger desligados ou autenticados no perfil `prod`.

**Matriz de acesso:**

| Recurso | Ler | Gravar |
| --- | --- | --- |
| Usuários | ADMIN | ADMIN |
| Ingredientes, embalagens e equipamentos | Todos os perfis | ADMIN, FORMULACAO |
| Produtos, fórmulas, garantias, etapas e SKUs | Todos os perfis | ADMIN, FORMULACAO |
| Clientes | Todos os perfis | ADMIN, VENDAS |
| Orçamentos, itens e mudança de status | Todos os perfis | ADMIN, VENDAS |

**Como o front vai guardar o token:** a opção mais segura com Next.js é o servidor do Next guardar o token num cookie `httpOnly`, `secure` e `sameSite=lax`, e repassá-lo à API. Assim o JavaScript do navegador nunca toca no token. Nas rotas do Next que gravam dados, confira também o header Origin, como proteção extra contra CSRF.

**Teste obrigatório desta fase:** para cada endpoint, um teste que chama sem token e espera 401, e um que chama com o perfil errado e espera 403.

## Fase 4 — Orçamentos e cálculo de custo (semanas 6 e 7)

É o coração do projeto e o que mais vai chamar atenção numa entrevista. O cálculo fica num módulo próprio, `costing`, separado de `budget`. Dentro dele, `costing/domain` é Java puro (sem Spring e sem banco) e guarda o `CostCalculator`, para poder testá-lo com facilidade. Um `CostingService` busca os dados, em lote, nas fachadas de `ingredient`, `packaging`, `formula` e `process` e chama o calculador. O `budget` só pede o custo pronto ao `costing` e nunca calcula nada.

**Três nomes para "custo":** use sempre o nome certo no código e na tela, para não misturar os conceitos.

- **Custo de aquisição:** quanto o ingrediente ou a embalagem custa para a fábrica (`custo_kg`, `custo_unitario`). Fica em `ingredient` e `packaging`.
- **Custo padrão:** quanto o produto custa hoje, calculado pelo `costing` com os preços atuais.
- **Custo cotado:** o custo padrão congelado no item do orçamento no momento do cálculo. Só muda com o recálculo explícito.

**Como o custo é calculado (granel por kg, depois o SKU embalado):**

```latex
C_{granel}(p) = \frac{\sum_{i \in I(p)} \frac{pct_i}{100} \, B \, c_i \;+\; \sum_{e \in E(p)} \left( \frac{M_e}{cap_e} + \frac{setup_e}{60} \right) h_e}{M_{final}}, \qquad M_e = B \prod_{k < e} \left( 1 - \frac{perda_k}{100} \right)
```

```latex
C_{SKU}(s) = \frac{peso_s}{1 - perda_{env}/100} \, C_{granel}(g) + \sum_{k \in Emb(s)} q_k \, c_k + \left( \frac{peso_s}{cap_{env}} + \frac{setup_{env}/60}{lote_s} \right) h_{env}
```

A primeira fórmula dá o custo por kg do granel e também do premix. O numerador é o custo dos ingredientes de uma batelada de B kg (o `tamanho_batelada_kg` do produto) mais o custo de cada etapa: tempo de máquina para a massa que **entra** na etapa (Mₑ, que diminui a cada perda anterior), mais setup, vezes o custo por hora. O denominador é a massa que realmente sai depois de todas as perdas (umidade na secagem, pó na moagem). As perdas ficam em % no banco e viram fração num só lugar. Quando um item da fórmula é um premix, o cᵢ dele é o custo por kg do próprio premix, calculado pela mesma fórmula, de forma recursiva.

A segunda dá o custo unitário do SKU embalado: a quantidade de granel necessária (peso líquido corrigido pela perda no envase) vezes o custo por kg do granel, mais as embalagens, mais o ensacamento. No ensacamento, o tempo de máquina é proporcional ao peso do saco, e o setup da ensacadeira é dividido pelo `tamanho_lote_envase` do SKU.

O preço de venda usa **markup** sobre o custo: `preco_venda = custo_unitario × (1 + markup_pct / 100)`, sem impostos. Com custo de R$ 100 e markup de 30%, o preço é R$ 130, o que dá uma margem real de cerca de 23% sobre o preço. A tela deve deixar claro que o percentual é markup.

**Passos:**

- [ ] Criar o módulo `costing`, com `costing/domain` em Java puro e o `CostingService`, que lê os dados pelas fachadas dos outros módulos.
- [ ] Implementar `CostCalculator` com o custo do premix e do granel (recursivo, porque um granel pode usar um premix) e a explosão da estrutura do SKU, incluindo perda e lote de envase, retornando um detalhamento (ingredientes, processo e embalagem separados).
- [ ] Memoizar o custo de cada premix e granel durante um mesmo cálculo: os SKUs de 1 kg e 15 kg usam a mesma ração, e várias rações podem usar o mesmo premix.
- [ ] `GET /api/products/{id}/cost` devolvendo o custo padrão de um produto com o detalhamento, sem precisar de orçamento.
- [ ] CRUD de orçamento e itens: `POST /api/budgets`, `POST /api/budgets/{id}/items`, `PUT` e `DELETE` em `/api/budgets/{id}/items/{itemId}`, com a validade do orçamento obrigatória ao criar e o número gerado por uma sequence do banco.
- [ ] Ao adicionar ou alterar item, pedir o custo ao `costing` e gravar `custo_ingredientes`, `custo_processo`, `custo_embalagem`, `custo_unitario`, `custo_kg`, `detalhamento_custo` (JSON com o custo por ingrediente, etapa e embalagem naquele momento) e `preco_venda = custo_unitario × (1 + markup_pct / 100)`, com a quantidade sempre em unidades do SKU.
- [ ] Eventos com outbox que deixam orçamentos desatualizados: `ingredient.ingredient.cost-changed`, `packaging.packaging.cost-changed`, `formula.product.changed`, `process.equipment.cost-changed` e `process.step.changed`, gravados na mesma transação da alteração, com IDs e valores no payload. O `budget` escuta e marca como `desatualizado` os orçamentos em RASCUNHO afetados. O consumidor é idempotente: processar o mesmo evento duas vezes dá o mesmo resultado.
- [ ] `POST /api/budgets/{id}/recalculate` para atualizar com os dados atuais e desmarcar `desatualizado`. Itens cujo produto voltou para RASCUNHO ficam marcados como `invalido`.
- [ ] `GET /api/budgets/{id}/cost-breakdown` devolvendo o detalhamento **gravado** em cada item (o custo cotado), e não o custo de hoje.
- [ ] Máquina de estados com `POST /api/budgets/{id}/send`, `/approve` e `/reject`: só RASCUNHO pode ser editado; ENVIADO pode virar APROVADO ou RECUSADO; transição inválida retorna 409. Não enviar orçamento desatualizado ou com item inválido (precisa recalcular antes) e não aprovar depois da validade.
- [ ] Lock otimista com `@Version` no orçamento: se dois usuários editarem ao mesmo tempo, o segundo recebe 409.
- [ ] Listagem com paginação e filtros no banco (cliente, status, período) usando `Specification` ou consultas JPQL.
- [ ] Arredondamento consistente: `setScale(2, RoundingMode.HALF_EVEN)` apenas no resultado final.

**Desafio avançado:** formulação a custo mínimo. Dados os preços dos ingredientes e os níveis de garantia, encontrar os percentuais que atendem os nutrientes com o menor custo. É um problema de programação linear clássico da indústria de ração; dá para resolver em Java com a biblioteca ojAlgo ou o Google OR-Tools.

**Outro desafio avançado:** momento de inclusão do ingrediente. Gordura e palatabilizante entram no recobrimento, depois da secagem, e não perdem água. Um campo `etapa_inclusao` (MISTURA ou RECOBRIMENTO) em `formula_items` deixa o cálculo das perdas e da composição nutricional mais fiel.

## Fase 5 — Testes (semana 8, mas escritos desde a Fase 2)

Os testes são escritos junto com cada unidade desde a Fase 2. A semana 8 serve para fechar lacunas, ligar as metas de cobertura no CI e conferir tudo contra o cenário de referência. A pirâmide é larga embaixo: a maior parte são testes do domínio em Java puro, que rodam em milissegundos, e poucos testes sobem o Spring ou o banco.

| Camada | Ferramenta | O que cobre | Proporção |
| --- | --- | --- | --- |
| Unitário do domínio | JUnit 5, AssertJ, testes parametrizados | `costing/domain`, nutrição, `BudgetStatus`, value objects | cerca de 70% |
| Service | Mockito e fachadas falsas em memória | Regras que dependem de outro módulo | cerca de 15% |
| Controller | `@WebMvcTest` + MockMvc | Validação, `ProblemDetail`, 401 e 403 | cerca de 10% |
| Integração | `@SpringBootTest` + Testcontainers (PostgreSQL) + Spring Modulith | Migrações, consultas, eventos com outbox, fluxo completo | cerca de 5% |
| Ponta a ponta (opcional) | Playwright | Login e criação de orçamento | 1 ou 2 testes |

**Metas:** 90% de linhas em `costing/domain` e na nutrição do `formula`, e 70% nos módulos `formula` e `budget`. O JaCoCo faz o build falhar abaixo disso. Não vale a pena testar getters, records, configuração do Spring nem componentes gerados.

**Cenário de referência.** O mesmo cenário fica na planilha de conferência em `docs/` e num builder de teste (`ReferenceScenario`). Monte a planilha antes do código e confira que ela chega aos valores esperados.

- **Premix vitamínico:** batelada de 100 kg; calcário 70% a R$ 0,40/kg e núcleo vitamínico 30% a R$ 20,00/kg; mistura a 500 kg/h, setup de 10 min, R$ 120/h, perda de 0,5%.
- **Ração Cão Adulto (granel):** batelada de 1.000 kg; milho 55% (R$ 1,20/kg), farelo de soja 25% (R$ 2,40/kg), farinha de carne 12% (R$ 3,00/kg), gordura de frango 6% (R$ 6,50/kg) e premix 2%.
- **Etapas do granel:** moagem (2.000 kg/h, setup de 15 min, R$ 150/h, perda de 1%), mistura (2.000 kg/h, 10 min, R$ 120/h), extrusão (1.000 kg/h, 30 min, R$ 400/h), secagem (1.000 kg/h, R$ 250/h, perda de 15%) e resfriamento (1.000 kg/h, R$ 100/h).
- **SKU de 15 kg:** saco a R$ 2,80 e etiqueta a R$ 0,15; ensacadeira a 3.000 kg/h, setup de 20 min, R$ 90/h, perda de 0,5% e lote de envase de 2.000 sacos; markup de 30%.
- **Nutrição (proteína bruta / umidade, em %):** milho 8 / 13, soja 46 / 12, carne 50 / 8, gordura 0 / 1, premix 0 / 1. Umidade final de 10% e garantia de proteína mínima de 22%.

| Valor | Esperado |
| --- | --- |
| Custo por kg do premix | R$ 6,7538 |
| Custo dos ingredientes da batelada | R$ 2.145,0754 |
| Custo do processo da batelada | R$ 1.119,5500 |
| Massa final | 841,500 kg |
| Custo por kg do granel | R$ 3,8795 |
| Granel por saco de 15 kg | 15,0754 kg |
| Custo do ensacamento por saco | R$ 0,4650 |
| Custo unitário do SKU de 15 kg | R$ 61,9004 (R$ 4,1267/kg) |
| Preço de venda com markup de 30% | R$ 80,47 |
| Proteína na massa / no produto final | 21,90% / 22,19% |
| Com o milho a R$ 1,50/kg: granel / SKU / preço | R$ 4,0756 / R$ 64,8563 / R$ 84,31 |

Cada caso abaixo tem um ID, que vai no `@DisplayName` do teste (por exemplo, `@DisplayName("C03: mistura processa a massa que sobrou da moagem")`). Assim dá para ligar cada teste a esta tabela.

**Cálculo de custo** (`CostCalculator`, unitário, sem Spring)

| ID | Caso | Esperado |
| --- | --- | --- |
| C01 | Cenário de referência completo | Granel R$ 3,8795/kg e SKU de 15 kg R$ 61,9004 |
| C02 | Granel com uma etapa e sem perdas (caso mínimo) | (ingredientes + etapa) / batelada |
| C03 | A massa que entra diminui depois de uma perda | Com 1% de perda na moagem, a mistura processa 990 kg e custa R$ 79,40, e não R$ 80,00 |
| C04 | Perda em % convertida uma vez só | Massa final de 841,5 kg; o teste falha se 15% for usado como 15 |
| C05 | Setup entra uma vez por batelada | Dobrar a batelada não dobra o custo do setup |
| C06 | Premix calculado de forma recursiva | Premix a R$ 6,7538/kg, que soma R$ 135,0754 na batelada do granel |
| C07 | Memoização | SKUs de 1 kg e 15 kg do mesmo granel: o granel é calculado uma vez (contar as chamadas ao provedor falso) |
| C08 | Setup do envase dividido pelo lote | R$ 30,00 de setup / 2.000 sacos = R$ 0,0150 por saco; ensacamento total de R$ 0,4650 por saco |
| C09 | Perda no envase | 15,0754 kg de granel por saco de 15 kg |
| C10 | Markup | Custo de R$ 100 e markup de 30% dão R$ 130,00; markup de 0% dá preço igual ao custo |
| C11 | Arredondamento | Intermediários sem arredondar; custo com 4 casas e preço com 2 casas, HALF\_EVEN (80,4705 vira 80,47; 2,345 vira 2,34; 2,355 vira 2,36) |
| C12 | Entradas impossíveis | Capacidade 0, batelada 0 ou perdas que zeram a massa geram exceção de domínio, nunca divisão por zero |
| C13 | O detalhamento fecha com o total | A soma das parcelas por ingrediente, etapa e embalagem é igual ao custo unitário |

**Fórmula e nutrição** (unitário e service com fachadas falsas)

| ID | Caso | Esperado |
| --- | --- | --- |
| F01 | Fórmula somando 99,50% | O rascunho é salvo; ativar retorna 422 com "A soma da fórmula é 99,50%. Ela precisa ser 100%." |
| F02 | Soma de 100,005% (dentro da tolerância) | Ativa |
| F03 | Nutrição do cenário de referência | Proteína de 21,90% na massa e 22,19% no produto final; ativa com mínimo de 22% |
| F04 | Mesma fórmula com mínimo de 22,5% | Continua RASCUNHO; a mensagem cita 22,19% abaixo de 22,50% |
| F05 | Garantia máxima | Um nutriente acima do máximo impede a ativação |
| F06 | Premix na nutrição | O premix entra com a composição calculada pela mesma regra |
| F07 | Ciclo direto e indireto | Premix que contém o granel que o usa, ou A → B → C → A, retorna 422 |
| F08 | Item da fórmula com ingrediente e premix ao mesmo tempo, ou com nenhum | 400 |
| F09 | Ingrediente inexistente ou inativo (a fachada falsa não encontra) | 422 ao salvar a fórmula |
| F10 | SKU sem granel, com dois granéis, sem embalagem ou com saco de 10 kg para 15 kg | 422 |
| F11 | Editar a fórmula de um produto ATIVO | Volta para RASCUNHO e publica `formula.product.changed` |
| F12 | Ingrediente desativado | As fórmulas que o usam voltam para RASCUNHO; um produto usado em outro SKU não pode ser desativado (409) |
| F13 | `PUT /steps` e `PUT /guarantees` | Substituem a lista inteira, e a ordem da lista vira a sequência |

**Orçamentos** (service, controller e integração)

| ID | Caso | Esperado |
| --- | --- | --- |
| O01 | Criar sem validade, ou com validade no passado | 400 |
| O02 | Adicionar SKU cuja fórmula está em RASCUNHO | 422 |
| O03 | Adicionar 10 unidades do SKU do cenário | Grava custo unitário de R$ 61,9004, preço de R$ 80,47 e o detalhamento em JSON, que soma o custo unitário |
| O04 | Mudar o milho para R$ 1,50 depois do item gravado | O item mantém os valores e o orçamento fica desatualizado |
| O05 | Recalcular o orçamento do O04 | Custo de R$ 64,8563, preço de R$ 84,31 e desatualizado = false |
| O06 | Transições válidas | RASCUNHO → ENVIADO → APROVADO e ENVIADO → RECUSADO |
| O07 | Transições inválidas (teste parametrizado) | Aprovar RASCUNHO, editar ENVIADO, enviar APROVADO e recalcular ENVIADO retornam 409 |
| O08 | Enviar orçamento desatualizado ou com item inválido | 409, com mensagem pedindo o recálculo |
| O09 | Aprovar depois da validade | 409 (com `Clock` injetado e data fixa no teste) |
| O10 | Produto de um item volta para RASCUNHO | O recálculo marca o item como inválido |
| O11 | Duas edições com a mesma versão | A segunda recebe 409 (lock otimista) |
| O12 | `cost-breakdown` depois de mudança de preço | Devolve o detalhamento gravado, e não o de hoje |
| O13 | Listagem | Filtros por cliente, status e período; `sort` por campo não permitido retorna 400; `size` acima de 100 é limitado |
| O14 | Duas criações ao mesmo tempo (integração) | Números de orçamento diferentes |

**Eventos e arquitetura** (integração)

| ID | Caso | Esperado |
| --- | --- | --- |
| E01 | Mudar o custo de um ingrediente | O evento fica gravado no outbox na mesma transação; se a transação falhar, não há evento |
| E02 | Entregar o mesmo evento duas vezes | O orçamento é marcado uma vez, sem erro (consumidor idempotente) |
| E03 | Cada um dos cinco eventos de custo | Marca só os orçamentos em RASCUNHO afetados; ENVIADO não muda |
| E04 | `ApplicationModules.verify()` | Passa: sem ciclos e sem acesso a pacotes internos |
| E05 | Banco do zero | As migrações Flyway aplicam no PostgreSQL do Testcontainers e a aplicação sobe com `ddl-auto=validate` |

**Segurança e API** (controller)

| ID | Caso | Esperado |
| --- | --- | --- |
| S01 | Cada endpoint sem token e com perfil errado | 401 e 403. Os casos são gerados a partir da matriz de acesso da Fase 3, num teste parametrizado |
| S02 | Corpo inválido | 400 com o campo `errors` por campo no `ProblemDetail` |
| S03 | Login com email inexistente e com senha errada | A mesma mensagem genérica nos dois casos |
| S04 | Refresh token usado duas vezes | O segundo uso falha e a família inteira é revogada |
| S05 | Sexta tentativa de login em um minuto | 429 |
| S06 | Erro inesperado | 500 sem stack trace na resposta |

**Checklist da semana 8:**

- [ ] Planilha de conferência em `docs/` com o cenário de referência, chegando aos valores da tabela.
- [ ] Builders de teste: `ReferenceScenario`, `ProductTestBuilder` e `BudgetTestBuilder`.
- [ ] Fachadas falsas em memória para testar services sem banco.
- [ ] `Clock` injetado em tudo que depende de data (validade do orçamento, expiração de token).
- [ ] JaCoCo no `./mvnw verify`, com as metas de cobertura fazendo o build falhar.
- [ ] Testes de integração com Testcontainers rodando no CI.
- [ ] Todos os IDs desta fase com pelo menos um teste.
- [ ] Opcional: um teste ponta a ponta com Playwright cobrindo login e criação de orçamento.

## Fase 6 — Front-end Next.js (semanas 9 e 10)

Aqui você está em casa; o foco é não repetir os desvios de padrão que o madepar tinha. Defina uma única forma de buscar e gravar dados e siga-a em todas as telas.

**Fundação:**

- [ ] Projeto Next.js com App Router, TypeScript strict, Tailwind e shadcn/ui; ESLint, Prettier e lefthook como no madepar.
- [ ] Gerar os tipos TypeScript a partir do OpenAPI do backend (`openapi-typescript`), para o front nunca divergir da API.
- [ ] Um único cliente HTTP no servidor do Next que injeta o token do cookie `httpOnly` e trata 401 com refresh.
- [ ] Login com Route Handler gravando o cookie; `middleware.ts` protegendo as rotas privadas.
- [ ] Escolher um padrão de dados e manter: TanStack Query no cliente para listas e mutações (com invalidação de cache em vez de `router.refresh()`).

**Telas, na ordem:**

1. Login.
2. Layout com menu lateral, exibindo apenas as opções permitidas ao perfil.
3. Listagens de ingredientes, embalagens, clientes, equipamentos e produtos com paginação, busca e filtros vindos do servidor.
4. Formulários com react-hook-form e zod, mostrando erros que vêm da API no campo certo.
5. Editor de fórmula: tabela de ingredientes com percentual, total ao vivo (precisa fechar 100%) e painel com a composição nutricional comparada aos níveis de garantia.
6. Tela de SKU (granel + embalagem) e tela de etapas do processo, ordenáveis.
7. Orçamentos: lista, criação, edição de itens e painel de resumo de custo (ingredientes, processo, embalagem, custo por kg, markup e preço sem impostos).
8. Detalhamento de custo de um item, com um gráfico mostrando quanto cada ingrediente pesa no custo.

**Detalhes de qualidade:** `<html lang="pt-BR">`, formatadores de moeda e data em um único arquivo, estados de carregando e vazio em todas as listas, toasts com sonner e confirmação antes de excluir.

## Fase 7 — Docker, CI, deploy e portfólio (semanas 11 e 12)

O projeto só conta no portfólio se alguém conseguir rodar ou acessar em poucos minutos.

**Docker e CI:**

- [ ] `Dockerfile` multi-stage para o backend (build com Maven, imagem final com JRE 21 slim) e para o front (`output: 'standalone'`).
- [ ] `docker-compose.yml` subindo banco, API e front com um único comando, lendo um `.env`.
- [ ] GitHub Actions: no pull request, rodar lint, testes do backend (Testcontainers funciona no runner) e do front, e o build.
- [ ] Badge de status do CI e de cobertura no README.

**Deploy (opções gratuitas ou baratas):**

- [ ] Banco gerenciado: Neon ou Supabase (PostgreSQL).
- [ ] API: Render, Railway ou Fly.io a partir da imagem Docker.
- [ ] Front: Vercel.
- [ ] Criar usuários de demonstração (um por perfil) e dados de exemplo, e mostrar as credenciais no README.

**Checklist de deploy** (use a cada deploy, não só no primeiro). A ordem é sempre: backup do banco, API (o Flyway roda na subida) e depois o front.

**Variáveis de ambiente de produção:**

| Variável | Onde | Observação |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | API | Sempre `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | API | Banco gerenciado, com SSL; usuário sem permissão de superusuário |
| `JWT_SECRET` | API | Gerado só para produção, com pelo menos 256 bits, diferente do de desenvolvimento |
| `JWT_ISSUER` | API | O mesmo valor validado no `iss` do token |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | API | Primeiro ADMIN; troque a senha depois do primeiro login |
| `CORS_ALLOWED_ORIGINS` | API | Só o domínio do front na Vercel |
| `API_BASE_URL` | Front (só servidor) | Endereço da API. Nunca use `NEXT_PUBLIC_` para endereço interno ou segredo |

As variáveis do usuário de desenvolvimento da Fase 2 (HTTP Basic) **não** existem em produção. A API deve falhar ao subir se faltar alguma variável obrigatória (`@ConfigurationProperties` com `@Validated`), em vez de subir com valor vazio.

**Migrações Flyway:**

- [ ] As migrações aplicam do zero (teste E05) e também sobre o estado atual de produção. Confira com `flyway info` numa cópia do banco (um branch do Neon ou um backup restaurado).
- [ ] Nenhuma migração já aplicada foi editada: o `flyway validate` passa, sem diferença de checksum.
- [ ] As migrações são compatíveis com a versão anterior da API (expandir e depois contrair): coluna nova entra como opcional, é preenchida e só vira obrigatória num deploy seguinte. Nada é renomeado ou removido no mesmo deploy em que o código deixa de usar. Assim dá para voltar a API sem voltar o banco.
- [ ] O seed do perfil `dev` não roda em produção. Os dados e usuários de demonstração vêm de uma pasta própria de migrações (`db/demo`), ligada só no ambiente de demonstração.
- [ ] A tabela de eventos do Spring Modulith (outbox) é criada por migração Flyway, com a criação automática do Modulith desligada.
- [ ] `spring.flyway.clean-disabled=true` e `spring.jpa.hibernate.ddl-auto=validate` no perfil `prod`.
- [ ] Backup do banco feito antes do deploy.

**Antes do deploy:**

- [ ] CI verde no commit exato que vai para produção: `./mvnw verify` com as metas de cobertura, `pnpm lint`, `pnpm test` e `pnpm build`.
- [ ] Versão marcada com uma tag (`v1.0.0`) e changelog atualizado.
- [ ] Imagem Docker construída no CI a partir da tag, e não na sua máquina.
- [ ] Nenhum segredo no repositório nem no histórico (por exemplo, com gitleaks) e nenhuma dependência com vulnerabilidade crítica (Dependabot).
- [ ] Perfil `prod` revisado: só `/actuator/health` exposto, Scalar e Swagger desligados ou autenticados, cookies com `secure`, HTTPS e logs sem senhas ou tokens.
- [ ] Health check da hospedagem apontando para `/actuator/health`, e limite de memória da JVM ajustado ao plano (por exemplo, `-XX:MaxRAMPercentage=75`).
- [ ] Tag da imagem anterior anotada, para o rollback.

**Depois do deploy (teste de fumaça):**

- [ ] `/actuator/health` responde 200, e `/scalar` não está aberto para quem não está logado.
- [ ] Login funciona com o usuário de demonstração de cada perfil, e uma chamada sem token retorna 401.
- [ ] O custo do SKU de 15 kg dos dados de demonstração bate com a planilha de conferência.
- [ ] Criar, recalcular e enviar um orçamento de teste funciona de ponta a ponta pelo front.
- [ ] Nos primeiros 15 minutos, os logs não têm erro 5xx e a tabela de eventos não acumula eventos pendentes.
- [ ] README atualizado com o link do deploy e com as notas da versão.

**Quando voltar atrás (rollback):**

- A API não sobe porque uma migração falhou: corrija com uma migração nova, nunca editando a que falhou.
- Login, cálculo de custo ou orçamento falham no teste de fumaça, ou o custo do SKU de referência não bate com a planilha.
- Qualquer erro 5xx nos fluxos principais logo depois do deploy.

Como voltar: publique de novo a imagem da versão anterior. Como as migrações são compatíveis com a versão anterior, o banco fica como está. Restaurar o backup é o último recurso, porque perde o que foi gravado depois dele.

**README que vende o projeto.** Ele tem dois leitores: o recrutador, que dá 30 segundos e lê só o topo, e o desenvolvedor, que quer rodar o projeto. Por isso o mais útil vem primeiro (demonstração e problema) e os detalhes técnicos vêm depois. Mostre em vez de explicar (GIF, comandos, números) e aponte para os documentos em `docs/` em vez de copiar o conteúdo deles.

| # | Seção | O que mostrar | Para quem |
| --- | --- | --- | --- |
| 1 | Título e uma linha | "Kibble: cálculo de custo de produção e orçamentos para fábricas de ração". Badges de CI, cobertura, Java 21 e Spring Boot 4 | Recrutador |
| 2 | Demonstração | Link do deploy, uma tabela com o usuário de demonstração de cada perfil e um GIF de até 30 segundos do fluxo principal (ativar uma fórmula e montar um orçamento) | Recrutador |
| 3 | O problema e a solução | Três ou quatro frases: o que a fábrica precisa saber (quanto custa cada kg e cada saco) e o que o sistema faz de diferente (perdas do processo, premix, umidade final, custo congelado no orçamento) | Recrutador |
| 4 | Funcionalidades | Lista curta por perfil: Formulação, Vendas e Administração | Recrutador |
| 5 | Como o custo é calculado | As duas fórmulas em uma frase cada e o cenário de referência como prova (granel a R$ 3,8795/kg, SKU de 15 kg a R$ 61,90, preço de R$ 80,47), com link para a planilha em `docs/` | Os dois |
| 6 | Arquitetura | Diagrama dos módulos e do caminho navegador → Next (BFF) → API → PostgreSQL, em Mermaid (o GitHub desenha sozinho), e o diagrama ER. Link para o ADR-0001 | Desenvolvedor |
| 7 | Stack | Tabela curta com tecnologia e versão | Os dois |
| 8 | Rodando localmente | Pré-requisitos (Docker, e Java 21 e pnpm só para desenvolver), os comandos (`git clone`, `cp .env.example .env`, `docker compose up --build`), os endereços (front, API, Scalar) e o usuário de teste. Meta: funcionando em menos de 5 minutos numa máquina limpa | Desenvolvedor |
| 9 | Desenvolvimento e testes | Como rodar back e front separados, `./mvnw verify`, `pnpm test`, `pnpm generate:api`, a estrutura de pastas resumida e a pirâmide de testes com a cobertura atual | Desenvolvedor |
| 10 | API | Link do Scalar (local e no deploy), como autenticar e um exemplo de erro em `ProblemDetail` | Desenvolvedor |
| 11 | Decisões técnicas | Uma linha por decisão, com link para o ADR em `docs/adr/`: monólito modular, sem chave estrangeira entre módulos, outbox, custo congelado no item, markup, premix, JWT com refresh token, BFF, `BigDecimal` | Recrutador e entrevistador |
| 12 | Segurança | Resumo do que está protegido (JWT curto, refresh com rotação, token fora do navegador, rate limit) e como reportar um problema | Desenvolvedor |
| 13 | Próximos passos | A evolução depois do MVP: lotes e rastreabilidade, integração com ERP (Projeto 2) e o app de apontamento (Projeto 4) | Recrutador |
| 14 | Autor e licença | Seu nome, links do LinkedIn e do GitHub e a licença (por exemplo, MIT) | Recrutador |

O que não entra no README: o checklist de deploy vai para `docs/deploy.md`, os ADRs para `docs/adr/` e o diagrama ER para `docs/`. O README só aponta para eles. Se o foco forem vagas fora do Brasil, escreva o README em inglês e mantenha uma versão `README.pt-BR.md`.

- [ ] As seções acima, nesta ordem.
- [ ] Rodar localmente testado do zero numa máquina limpa (ou num codespace), seguindo só o README.
- [ ] GIF e prints da versão atual das telas.
- [ ] Todos os links funcionando (deploy, Scalar, ADRs, planilha) e os badges verdes.
- [ ] As credenciais do README são só dos usuários de demonstração, e nenhum segredo aparece nos exemplos.

**Extra para entrevista:** escreva um post curto no LinkedIn ou no dev.to contando como você calculou o custo por kg considerando as perdas do processo.

## Evolução depois do MVP

As regras entre módulos da Fase 0 existem para que as próximas funcionalidades entrem como **módulos novos**, sem reescrever os atuais. Hoje o sistema é um deploy e um banco, e continua assim até que métricas justifiquem outra coisa.

**Lotes e rastreabilidade**

| Módulo novo | O que faz | Depende de |
| --- | --- | --- |
| `inventory` | Lote de **insumo**: recebimento de ingrediente com lote do fornecedor, validade e quantidade | `ingredient` (por ID) |
| `production` | Ordem de produção e lote **produzido**: batelada real, lotes de insumo consumidos e perdas reais | `formula`, `process`, `inventory` |
| `traceability` | Consultas para frente e para trás (este lote de milho foi para quais SKUs e clientes?), montadas a partir de eventos | Eventos de `inventory` e `production` |

- "Lote de insumo" e "lote de produção" são coisas diferentes e têm nomes diferentes desde o início.
- O lote produzido e os lotes de insumo consumidos ficam num único agregado do `production`, numa só transação. A baixa no estoque acontece pelo evento `production.batch.completed`, porque é escrita em outro módulo.
- A ordem de produção congela a versão da fórmula, como o orçamento congela o custo. Aí as versões de fórmula, hoje fora do MVP, passam a ser necessárias.
- O cadastro de ingrediente continua sem lote: lote é estoque, não cadastro. Por causa da referência por ID, `formula` e `costing` não mudam.
- O `production` também prepara o Projeto 4 (app de apontamento).

**Integração com ERP (Projeto 2)**

- Um módulo `erp` na borda, como camada anticorrupção. Os módulos de domínio nunca sabem que o ERP existe.
- Portas na linguagem do Kibble: `IngredientPriceSource` (entrada de preços) e `SalesOrderPublisher` (saída do pedido). O adaptador (por exemplo, `ProtheusSoapAdapter`) é o único que conhece SOAP e os códigos do ERP, guardados numa tabela própria do `erp`. Trocar de ERP é trocar o adaptador.
- Os preços entram pela fachada do `ingredient`, o que dispara o mesmo evento de mudança de custo. O pedido sai quando o `erp` recebe `budget.budget.approved` pelo outbox, então o ERP fora do ar não impede a aprovação.
- Resiliência no adaptador com Resilience4j, nesta ordem: timeout, circuit breaker e retry com backoff exponencial e jitter. Retry só em operação idempotente, com o ID do orçamento como chave de idempotência e um limite de tentativas.

**Separar um módulo em serviço (Projeto 5)**

- Sem chave estrangeira entre módulos, com fachadas e com outbox, a extração é mecânica: a fachada vira um cliente HTTP e o outbox passa a publicar num broker.
- Só vale a pena quando as métricas do próprio módulo justificarem (carga, falhas, ritmo de mudança), e não porque "o sistema ficou grande".

## Cronograma, critérios de pronto e materiais

O plano cabe em 12 semanas com cerca de 10 horas por semana; se tiver menos tempo, estique os prazos, mas mantenha a ordem.

&#91;embedded content: cronograma do Projeto 1 · 8 fases em 12 semanas\]

A Fase 4 está destacada porque é onde mora o valor do projeto: se o tempo apertar, corte telas do front, nunca o cálculo ou os testes dele.

**Critérios de pronto do projeto:**

- [ ] Um recrutador consegue abrir o link do deploy e entrar com um usuário de demonstração.
- [ ] `docker compose up` sobe tudo do zero numa máquina limpa.
- [ ] Nenhum segredo no código ou no histórico do git.
- [ ] Toda rota exige login e respeita o perfil, com teste provando isso.
- [ ] O custo de um SKU de 15 kg (fórmula, processo e embalagem) bate com a planilha de conferência.
- [ ] O CI está verde e o README explica as decisões técnicas.

**Materiais de estudo:**

- Documentação oficial do Spring Boot, Spring Security e Spring Data JPA (guias em spring.io/guides), mais o guia de migração para o Spring Boot 4, na wiki do projeto no GitHub.
- Baeldung, para dúvidas pontuais de Spring e JPA.
- Livro *Spring Boot Up & Running*, de Mark Heckler, bom para conceitos (foi escrito antes do Boot 4).
- Vlad Mihalcea (blog), para performance de JPA e o problema N+1.
- Documentação do Testcontainers, do Flyway e do Spring Modulith, e a do Resilience4j para a evolução com ERP.
- OWASP Top 10 e o guia de JWT da OWASP, para revisar a Fase 3.

**Dica de ritmo:** faça um commit pequeno por tarefa marcada aqui e abra pull requests para você mesmo. O histórico do git também é portfólio.
