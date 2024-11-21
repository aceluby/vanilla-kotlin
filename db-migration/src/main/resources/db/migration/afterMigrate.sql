--
-- the following statements are run after the migrate command has been executed.
--

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vanilla_kotlin_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO vanilla_kotlin_app;

-- After granting to all tables, we then revoke permissions to the flyway schema table
REVOKE ALL ON TABLE flyway_schema_history FROM vanilla_kotlin_app;
