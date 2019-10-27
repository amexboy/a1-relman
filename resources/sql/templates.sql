-- :name get-templates :query :many
SELECT "name", template, "required-args", "created-by"
FROM "templates";

-- :name insert-template :insert :1
INSERT INTO "templates"
("name", template, "required-args", "created-by")
VALUES(:name, :template, :required-args, :created-by);


-- :name get-template :query :1
SELECT "name", "template", "required-args", "created-by"
FROM "templates"
WHERE "name"=:name;

-- :name insert-request :insert :1 :<!
INSERT INTO "requests"
("template", "args", "request-for", "created-by" )
VALUES
(:template, to_json(:args), :request-for, :created-by) RETURNING ID;
