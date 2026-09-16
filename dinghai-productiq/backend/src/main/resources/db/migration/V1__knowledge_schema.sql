PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS documents (
    document_id TEXT PRIMARY KEY,
    source_key TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sections (
    section_id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    heading_path_json TEXT NOT NULL,
    summary TEXT NOT NULL,
    content TEXT NOT NULL,
    source_start_line INTEGER NOT NULL,
    source_end_line INTEGER NOT NULL,
    UNIQUE (section_id, document_id)
);

CREATE INDEX IF NOT EXISTS idx_sections_document ON sections(document_id);

CREATE TABLE IF NOT EXISTS concepts (
    concept_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    concept_type TEXT NOT NULL,
    definition TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS aliases (
    concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
    alias TEXT NOT NULL,
    alias_kind TEXT NOT NULL CHECK (alias_kind IN ('canonical', 'query')),
    PRIMARY KEY (concept_id, alias)
);

CREATE TABLE IF NOT EXISTS section_concepts (
    section_id TEXT NOT NULL REFERENCES sections(section_id) ON DELETE CASCADE,
    concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE RESTRICT,
    role TEXT NOT NULL CHECK (role IN ('primary', 'mentioned', 'rule_subject')),
    PRIMARY KEY (section_id, concept_id)
);

CREATE INDEX IF NOT EXISTS idx_section_concepts_concept ON section_concepts(concept_id);

CREATE TABLE IF NOT EXISTS relations (
    relation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE RESTRICT,
    relation_type TEXT NOT NULL,
    to_concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE RESTRICT,
    UNIQUE (from_concept_id, relation_type, to_concept_id)
);

CREATE INDEX IF NOT EXISTS idx_relations_from ON relations(from_concept_id);
CREATE INDEX IF NOT EXISTS idx_relations_to ON relations(to_concept_id);

CREATE TABLE IF NOT EXISTS relation_evidence (
    evidence_id INTEGER PRIMARY KEY AUTOINCREMENT,
    relation_id INTEGER NOT NULL REFERENCES relations(relation_id) ON DELETE CASCADE,
    document_id TEXT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    section_id TEXT NOT NULL REFERENCES sections(section_id) ON DELETE CASCADE,
    source_quote TEXT NOT NULL,
    source_hash TEXT NOT NULL,
    UNIQUE (relation_id, document_id, section_id, source_hash),
    FOREIGN KEY (section_id, document_id) REFERENCES sections(section_id, document_id)
);

CREATE INDEX IF NOT EXISTS idx_relation_evidence_document ON relation_evidence(document_id);

CREATE TABLE IF NOT EXISTS vector_index_jobs (
    job_id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_key TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('upsert', 'delete')),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'succeeded', 'failed')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_vector_index_jobs_status ON vector_index_jobs(status, job_id);
