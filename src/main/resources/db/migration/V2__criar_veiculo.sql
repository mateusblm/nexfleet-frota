CREATE TABLE frota.veiculo (
  id UUID PRIMARY KEY,
  placa VARCHAR(10) NOT NULL UNIQUE,
  marca VARCHAR(80) NOT NULL,
  modelo VARCHAR(80) NOT NULL,
  ano INTEGER NOT NULL CHECK (ano BETWEEN 1886 AND 9999),
  tipo VARCHAR(40) NOT NULL,
  quilometragem BIGINT NOT NULL CHECK (quilometragem >= 0),
  status VARCHAR(20) NOT NULL CHECK (
    status IN ('DISPONIVEL', 'EM_USO', 'EM_MANUTENCAO', 'INATIVO')
  ),
  data_aquisicao DATE NOT NULL,
  criado_por UUID NOT NULL,
  atualizado_por UUID NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (placa = upper(btrim(placa))),
  CHECK (char_length(placa) BETWEEN 1 AND 10),
  CHECK (char_length(btrim(marca)) BETWEEN 1 AND 80),
  CHECK (char_length(btrim(modelo)) BETWEEN 1 AND 80),
  CHECK (char_length(btrim(tipo)) BETWEEN 1 AND 40)
);

CREATE INDEX ix_veiculo_status_placa ON frota.veiculo(status, placa);

COMMENT ON TABLE frota.veiculo IS
  'Cadastro mestre de veículos; status INATIVO preserva referências e histórico.';
