ALTER TABLE tb_subscription ADD COLUMN asaas_checkout_id varchar(64) UNIQUE;
ALTER TABLE tb_subscription ADD COLUMN checkout_plan_code varchar(16) REFERENCES tb_plan (plan_code);
ALTER TABLE tb_subscription ADD COLUMN checkout_billing_cycle varchar(8) CHECK (checkout_billing_cycle IN ('MONTHLY', 'YEARLY'));

COMMENT ON COLUMN tb_subscription.asaas_checkout_id IS 'Checkout do Asaas aguardando conclusão pelo cliente.';
COMMENT ON COLUMN tb_subscription.checkout_plan_code IS 'Plano escolhido no checkout em aberto; só vale depois da conclusão.';
COMMENT ON COLUMN tb_subscription.checkout_billing_cycle IS 'Periodicidade escolhida no checkout em aberto.';
