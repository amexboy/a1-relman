-- :name get-services :query :many
SELECT "name", team, "slack-channel", "slack-group"
FROM "services";

-- :name insert-service :insert :1
INSERT INTO "services"
("name", team, "slack-channel", "slack-group")
VALUES(:name, :team, :slack-channel, :slack-group);

-- :name get-service :query :1
SELECT "name", team, "slack-channel", "slack-group"
FROM "services"
WHERE "name"=:name;

