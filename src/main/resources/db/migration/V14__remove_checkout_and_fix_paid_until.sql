-- O Checkout só guardava o cartão e cobrava no vencimento: a assinatura volta a ser paga pela fatura do Asaas.
ALTER TABLE tb_subscription DROP COLUMN asaas_checkout_id;
ALTER TABLE tb_subscription DROP COLUMN checkout_plan_code;
ALTER TABLE tb_subscription DROP COLUMN checkout_billing_cycle;

-- paid_until guardava o fim do teste em assinaturas sem pagamento; agora significa apenas período realmente pago.
UPDATE tb_subscription SET paid_until = NULL WHERE status = 'PENDING';
