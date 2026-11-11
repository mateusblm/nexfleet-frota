CREATE TABLE frota.veiculo_motorista (
  id UUID PRIMARY KEY,
  veiculo_id UUID NOT NULL REFERENCES frota.veiculo(id),
  motorista_id UUID NOT NULL REFERENCES frota.motorista(id),
  inicio_em TIMESTAMPTZ NOT NULL,
  fim_em TIMESTAMPTZ,
  criado_por UUID NOT NULL,
  encerrado_por UUID,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (fim_em IS NULL OR fim_em >= inicio_em),
  CHECK ((fim_em IS NULL AND encerrado_por IS NULL) OR (fim_em IS NOT NULL AND encerrado_por IS NOT NULL))
);

CREATE UNIQUE INDEX ux_veiculo_motorista_veiculo_ativo
  ON frota.veiculo_motorista(veiculo_id)
  WHERE fim_em IS NULL;

CREATE INDEX ix_veiculo_motorista_historico_veiculo
  ON frota.veiculo_motorista(veiculo_id, inicio_em DESC);

CREATE INDEX ix_veiculo_motorista_historico_motorista
  ON frota.veiculo_motorista(motorista_id, inicio_em DESC);

COMMENT ON TABLE frota.veiculo_motorista IS
  'Histórico de vínculos; no máximo um vínculo ativo por veículo.';
