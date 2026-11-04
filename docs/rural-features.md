# Recursos para propriedades rurais

## Cadastro por propriedade e talhão

Na página **Propriedades**, cada área pode armazenar cultura, identificação do
talhão, data de plantio e fase da lavoura, além do nome, localização e contorno.
Os novos campos são opcionais para manter compatibilidade com cadastros antigos.
As fases são informadas pelo usuário e não são calculadas automaticamente.

## Clima e chuva

O painel da propriedade consulta a previsão de sete dias da WeatherAPI para o
centro do polígono. A chuva acumulada prevista soma os totais diários do período.
Para chuva observada, o painel procura a estação pluviométrica ANA/SNIRH mais
próxima em até 50 km e informa distância e horário da leitura. A estação pode
não representar a chuva dentro de toda a propriedade; se não houver leitura
próxima, o painel explica a indisponibilidade.

## Sinal para vistoria baseado em NDVI

Com pelo menos três observações e cobertura útil de pixels de 50% ou mais, a
página compara a leitura mais recente com a média das anteriores no período
selecionado. Uma queda relativa de pelo menos 15% e absoluta de 0,04 NDVI gera
um sinal para o usuário considerar uma vistoria. A mensagem inclui a cultura,
fase e talhão cadastrados. Isso é uma comparação simples com o histórico da
própria área, não um diagnóstico agronômico nem uma notificação automática.

## Caderno de campo e relatório

Cada propriedade possui registros de irrigação, vistoria e ocorrência, com
data, descrição e lâmina aplicada opcional para irrigação. Os registros são
isolados por propriedade e conta, podem ser excluídos e acompanham o resumo
imprimível da propriedade. Use **Imprimir resumo** e escolha “Salvar como PDF”
no diálogo de impressão para gerar um relatório.

## Banco existente com `JPA_DDL_AUTO=validate`

Em ambientes que não aplicam `ddl-auto=update`, adicione as colunas e a tabela
antes de iniciar a versão atual:

```sql
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS crop_name varchar(120);
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS plot_name varchar(120);
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS planting_date date;
ALTER TABLE tb_property ADD COLUMN IF NOT EXISTS crop_stage varchar(80);

CREATE TABLE IF NOT EXISTS tb_property_activity (
    activity_id uuid PRIMARY KEY,
    property_id uuid NOT NULL REFERENCES tb_property(property_id),
    activity_type varchar(24) NOT NULL,
    activity_date date NOT NULL,
    notes varchar(1000) NOT NULL,
    amount_mm double precision,
    created_at timestamp without time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_property_activity_property_date
    ON tb_property_activity(property_id, activity_date);
```
