COMMENT ON TABLE tb_plot_season IS 'Safras registradas para cada talhão, preservando o histórico dos ciclos de cultivo.';
COMMENT ON COLUMN tb_plot_season.season_id IS 'Identificador único da safra.';
COMMENT ON COLUMN tb_plot_season.plot_id IS 'Identificador do talhão ao qual a safra pertence.';
COMMENT ON COLUMN tb_plot_season.crop_name IS 'Nome da cultura cultivada durante a safra.';
COMMENT ON COLUMN tb_plot_season.variety IS 'Variedade da cultura, quando informada.';
COMMENT ON COLUMN tb_plot_season.planting_date IS 'Data de plantio que marca o início do ciclo da safra.';
COMMENT ON COLUMN tb_plot_season.harvest_date IS 'Data da colheita informada ao encerrar a safra.';
COMMENT ON COLUMN tb_plot_season.status IS 'Situação da safra: ativa ou encerrada.';
COMMENT ON COLUMN tb_plot_season.created_at IS 'Data e hora em que a safra foi cadastrada.';

COMMENT ON TABLE tb_activity_photo IS 'Fotografias anexadas aos registros de atividades do caderno de campo.';
COMMENT ON COLUMN tb_activity_photo.photo_id IS 'Identificador único da fotografia.';
COMMENT ON COLUMN tb_activity_photo.activity_id IS 'Identificador da atividade à qual a fotografia está anexada.';
COMMENT ON COLUMN tb_activity_photo.file_name IS 'Nome original do arquivo enviado.';
COMMENT ON COLUMN tb_activity_photo.content_type IS 'Tipo MIME do arquivo de imagem.';
COMMENT ON COLUMN tb_activity_photo.image_data IS 'Conteúdo binário da imagem armazenado no banco de dados.';
COMMENT ON COLUMN tb_activity_photo.created_at IS 'Data e hora em que a fotografia foi cadastrada.';

COMMENT ON COLUMN tb_property_activity.season_id IS 'Identificador da safra relacionada à atividade, quando informada.';
COMMENT ON COLUMN tb_property_activity.responsible_user_id IS 'Identificador do usuário responsável pela atividade, quando vinculado à equipe.';
COMMENT ON COLUMN tb_property_activity.responsible_name IS 'Nome do responsável informado para a atividade.';
COMMENT ON COLUMN tb_property_activity.input_name IS 'Nome do insumo utilizado na operação de campo.';
COMMENT ON COLUMN tb_property_activity.input_quantity IS 'Quantidade do insumo utilizado na operação de campo.';
COMMENT ON COLUMN tb_property_activity.input_unit IS 'Unidade de medida da quantidade do insumo.';
COMMENT ON COLUMN tb_property_activity.harvest_quantity IS 'Quantidade colhida registrada na atividade.';
COMMENT ON COLUMN tb_property_activity.harvest_unit IS 'Unidade de medida da quantidade colhida.';

COMMENT ON COLUMN tb_field_action.assignee_user_id IS 'Identificador do usuário da equipe designado para executar a ação.';
COMMENT ON COLUMN tb_field_action.completed_by_user_id IS 'Identificador do usuário que registrou a conclusão da ação.';
COMMENT ON COLUMN tb_field_action.reminder_sent_at IS 'Data e hora em que o lembrete da ação foi enviado.';

COMMENT ON COLUMN tb_property.action_reminder_enabled IS 'Indica se a propriedade envia lembretes por e-mail sobre ações com prazo.';
COMMENT ON COLUMN tb_property.action_reminder_days IS 'Número de dias de antecedência para enviar o lembrete da ação, de zero a sete.';
COMMENT ON COLUMN tb_property.require_season_for_field_records IS 'Indica se plantio, adubação, pulverização e colheita devem ser vinculados a uma safra.';
COMMENT ON COLUMN tb_property.default_harvest_unit IS 'Unidade de medida padrão sugerida para registros de colheita.';
