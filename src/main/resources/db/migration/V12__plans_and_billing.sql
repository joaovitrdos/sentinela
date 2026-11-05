CREATE TABLE tb_plan (
    plan_code varchar(16) PRIMARY KEY,
    name varchar(60) NOT NULL,
    monthly_price numeric(10, 2) NOT NULL CHECK (monthly_price > 0),
    yearly_price numeric(10, 2) NOT NULL CHECK (yearly_price > 0),
    max_properties integer CHECK (max_properties > 0),
    plan_rank integer NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE tb_plan_feature (
    plan_code varchar(16) NOT NULL REFERENCES tb_plan (plan_code) ON DELETE CASCADE,
    feature varchar(24) NOT NULL,
    PRIMARY KEY (plan_code, feature)
);

INSERT INTO tb_plan (plan_code, name, monthly_price, yearly_price, max_properties, plan_rank) VALUES
    ('NORMAL', 'Normal', 20.00, 200.00, 1, 1),
    ('MEDIO', 'Médio', 60.00, 600.00, 5, 2),
    ('PREMIUM', 'Premium', 120.00, 1200.00, NULL, 3);

INSERT INTO tb_plan_feature (plan_code, feature) VALUES
    ('NORMAL', 'BASE'),
    ('MEDIO', 'BASE'), ('MEDIO', 'REPORTS'), ('MEDIO', 'SPRAY_WINDOW'), ('MEDIO', 'NDVI'), ('MEDIO', 'ALERTS'), ('MEDIO', 'TEAM'),
    ('PREMIUM', 'BASE'), ('PREMIUM', 'REPORTS'), ('PREMIUM', 'SPRAY_WINDOW'), ('PREMIUM', 'NDVI'), ('PREMIUM', 'ALERTS'),
    ('PREMIUM', 'TEAM'), ('PREMIUM', 'IRRIGATION'), ('PREMIUM', 'EARLY_ACCESS');

CREATE TABLE tb_subscription (
    subscription_id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES tb_users (user_id) ON DELETE CASCADE,
    plan_code varchar(16) REFERENCES tb_plan (plan_code),
    pending_plan_code varchar(16) REFERENCES tb_plan (plan_code),
    billing_cycle varchar(8) CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    pending_billing_cycle varchar(8) CHECK (pending_billing_cycle IN ('MONTHLY', 'YEARLY')),
    status varchar(16) NOT NULL CHECK (status IN ('TRIAL', 'PENDING', 'ACTIVE', 'OVERDUE', 'CANCELED')),
    trial_ends_at timestamp NOT NULL,
    paid_until date,
    asaas_customer_id varchar(64),
    asaas_subscription_id varchar(64) UNIQUE,
    created_at timestamp NOT NULL
);

CREATE TABLE tb_asaas_event (
    event_id varchar(80) PRIMARY KEY,
    event_type varchar(60) NOT NULL,
    received_at timestamp NOT NULL
);

INSERT INTO tb_subscription (subscription_id, user_id, status, trial_ends_at, created_at)
SELECT gen_random_uuid(), user_id, 'TRIAL', now() + interval '7 days', now() FROM tb_users;

COMMENT ON TABLE tb_plan IS 'Planos comerciais; os valores são lidos daqui, nunca enviados pelo frontend.';
COMMENT ON COLUMN tb_plan.plan_code IS 'Código do plano.';
COMMENT ON COLUMN tb_plan.name IS 'Nome exibido do plano.';
COMMENT ON COLUMN tb_plan.monthly_price IS 'Valor da cobrança mensal, em reais.';
COMMENT ON COLUMN tb_plan.yearly_price IS 'Valor da cobrança anual, em reais.';
COMMENT ON COLUMN tb_plan.max_properties IS 'Limite de propriedades do dono; nulo indica sem limite.';
COMMENT ON COLUMN tb_plan.plan_rank IS 'Ordem do plano, do menor para o maior.';
COMMENT ON COLUMN tb_plan.active IS 'Indica se o plano está disponível para contratação.';
COMMENT ON TABLE tb_plan_feature IS 'Recursos liberados em cada plano.';
COMMENT ON COLUMN tb_plan_feature.plan_code IS 'Plano que libera o recurso.';
COMMENT ON COLUMN tb_plan_feature.feature IS 'Recurso liberado.';
COMMENT ON TABLE tb_subscription IS 'Situação de assinatura de cada usuário.';
COMMENT ON COLUMN tb_subscription.subscription_id IS 'Identificador da assinatura.';
COMMENT ON COLUMN tb_subscription.user_id IS 'Usuário assinante.';
COMMENT ON COLUMN tb_subscription.plan_code IS 'Plano contratado.';
COMMENT ON COLUMN tb_subscription.pending_plan_code IS 'Plano que entra em vigor no próximo pagamento confirmado.';
COMMENT ON COLUMN tb_subscription.billing_cycle IS 'Periodicidade contratada.';
COMMENT ON COLUMN tb_subscription.pending_billing_cycle IS 'Periodicidade que entra em vigor no próximo pagamento confirmado.';
COMMENT ON COLUMN tb_subscription.status IS 'Situação da assinatura.';
COMMENT ON COLUMN tb_subscription.trial_ends_at IS 'Fim do teste grátis.';
COMMENT ON COLUMN tb_subscription.paid_until IS 'Último dia coberto pelo pagamento confirmado.';
COMMENT ON COLUMN tb_subscription.asaas_customer_id IS 'Identificador do cliente no Asaas.';
COMMENT ON COLUMN tb_subscription.asaas_subscription_id IS 'Identificador da assinatura no Asaas.';
COMMENT ON COLUMN tb_subscription.created_at IS 'Data e hora de criação do registro.';
COMMENT ON TABLE tb_asaas_event IS 'Eventos de webhook já processados, para evitar processamento duplicado.';
COMMENT ON COLUMN tb_asaas_event.event_id IS 'Identificador do evento no Asaas.';
COMMENT ON COLUMN tb_asaas_event.event_type IS 'Tipo do evento.';
COMMENT ON COLUMN tb_asaas_event.received_at IS 'Data e hora de recebimento do evento.';
