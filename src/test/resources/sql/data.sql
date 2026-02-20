-- Users
INSERT INTO users (id, full_name, email, role, created_at)
VALUES (1, 'Lex Luthor', 'lex.luthor@gmail.com', 'ADMIN', '2025-11-15 08:30:00+00'),
       (2, 'Bruce Wayne', 'bruce.wayne@gmail.com', 'AGENT', '2025-12-05 10:00:00+00'),
       (3, 'Clark Kent', 'clark.kent@gmail.com', 'CUSTOMER', '2026-01-20 14:15:00+00');
SELECT setval('users_id_seq', (SELECT max(id) FROM users));

-- Tickets
INSERT INTO tickets (id, title, description, status, priority, created_by_id, assigned_to_id, created_at, modified_at,
                     resolved_at, version)
VALUES (1, 'Cannot log in', 'Getting error when logging in with Google',
        'IN_PROGRESS', 'HIGH', 3, 2,
        '2026-02-12 09:10:00+00', '2026-02-12 09:25:00+00', NULL, 0),

       (2, 'Billing discrepancy', 'Charged twice for last month',
        'NEW', 'MEDIUM', 3, NULL,
        '2026-02-18 16:05:00+00', '2026-02-18 16:05:00+00', NULL, 0),

       (3, 'Cannot upgrade my subscription', 'I want to upgrade my subscription to premium',
        'NEW', 'MEDIUM', 3, NULL,
        '2026-02-20 08:40:00+00', '2026-02-20 08:40:00+00', NULL, 0);
SELECT setval('tickets_id_seq', (SELECT max(id) FROM tickets));

-- Comments
INSERT INTO ticket_comments(id, ticket_id, author_id, body, created_at)
VALUES (1, 1, 3, 'Yesterday it was working, but today it is not', '2026-02-12 10:05:00+00'),
       (2, 1, 3, 'I have tried to reset my password multiple times, but it is not working', '2026-02-13 09:30:00+00');
SELECT setval('ticket_comments_id_seq', (SELECT max(id) FROM ticket_comments));

-- Events
INSERT INTO ticket_events (id, ticket_id, actor_id, event_type, payload, created_at)
VALUES (1, 1, 3, 'CREATED', '{"ticketId":"1","createdById":"3"}', '2026-02-12 09:10:00+00'),
       (2, 1, 2, 'ASSIGNED', '{"previousAssigneeId":null,"assigneeId":"2"}', '2026-02-12 09:12:00+00'),
       (3, 1, 2, 'PRIORITY_CHANGED', '{"oldPriority":"MEDIUM","newPriority":"HIGH"}', '2026-02-12 09:18:00+00'),
       (4, 1, 2, 'STATUS_CHANGED', '{"oldStatus":"NEW","newStatus":"IN_PROGRESS"}', '2026-02-12 09:25:00+00'),
       (5, 1, 3, 'COMMENTED', '{"commentId":"1"}', '2026-02-12 10:05:00+00'),
       (6, 1, 3, 'COMMENTED', '{"commentId":"2"}', '2026-02-13 09:30:00+00'),
       (7, 2, 3, 'CREATED', '{"ticketId":"2","createdById":"3"}', '2026-02-18 16:05:00+00'),
       (8, 3, 3, 'CREATED', '{"ticketId":"3","createdById":"3"}', '2026-02-20 08:40:00+00''');
SELECT setval('ticket_events_id_seq', (SELECT max(id) FROM ticket_events));
