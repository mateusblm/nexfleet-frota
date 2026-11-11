CREATE TABLE frota.motorista (
  id UUID PRIMARY KEY,
  nome VARCHAR(120) NOT NULL,
  cpf VARCHAR(11) NOT NULL UNIQUE,
  cnh VARCHAR(11) NOT NULL UNIQUE,
  categoria VARCHAR(5) NOT NULL,
  validade_cnh DATE NOT NULL,
  telefone VARCHAR(30) NOT NULL,
  status VARCHAR(10) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO')),
  criado_por UUID NOT NULL,
  atualizado_por UUID NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (char_length(btrim(nome)) BETWEEN 1 AND 120),
  CHECK (cpf ~ '^[0-9]{11}$'),
  CHECK (cnh ~ '^[0-9]{11}$'),
  CHECK (char_length(btrim(categoria)) BETWEEN 1 AND 5),
  CHECK (char_length(btrim(telefone)) BETWEEN 1 AND 30)
);

CREATE INDEX ix_motorista_status_nome ON frota.motorista(status, nome);

COMMENT ON TABLE frota.motorista IS
  'Cadastro de motoristas; CPF e CNH são dados pessoais com acesso autenticado.';
