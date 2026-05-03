-- Users
INSERT INTO users (id, full_name, email, role, created_at, password)
VALUES (1, 'Lex Luthor', 'lex.luthor@gmail.com', 'ADMIN', '2025-11-15 08:30:00+00', '{noop}123'),
       (2, 'Bruce Wayne', 'bruce.wayne@gmail.com', 'AGENT', '2025-12-05 10:00:00+00', '{noop}123'),
       (3, 'Clark Kent', 'clark.kent@gmail.com', 'CUSTOMER', '2026-01-20 14:15:00+00', '{noop}123'),
       (4, 'Oliver Queen', 'oliver.queen@gmail.com', 'CUSTOMER', '2026-02-10 11:20:00+00', '{noop}123');
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
        '2026-02-20 08:40:00+00', '2026-02-20 08:40:00+00', NULL, 0),

       (4, 'Refund request after cancellation', 'I cancelled my subscription, but I was still charged',
        'WAITING_CUSTOMER', 'LOW', 4, 2,
        '2026-02-22 13:15:00+00', '2026-02-23 09:45:00+00', NULL, 0),

       (5, 'Payment confirmation not received', 'The payment was completed, but I did not receive a confirmation email',
        'RESOLVED', 'MEDIUM', 4, 2,
        '2026-02-24 15:30:00+00', '2026-02-25 10:20:00+00', '2026-02-25 10:20:00+00', 0);
SELECT setval('tickets_id_seq', (SELECT max(id) FROM tickets));


-- Comments
INSERT INTO ticket_comments(id, ticket_id, author_id, body, created_at)
VALUES (1, 1, 3, 'Yesterday it was working, but today it is not', '2026-02-12 10:05:00+00'),
       (2, 1, 3, 'I have tried to reset my password multiple times, but it is not working', '2026-02-13 09:30:00+00'),

       (3, 4, 4, 'I cancelled the subscription before the renewal date, but the payment was still taken.',
        '2026-02-22 13:25:00+00'),
       (4, 4, 2, 'Could you please upload the payment confirmation or invoice so we can check it?',
        '2026-02-23 09:45:00+00'),

       (5, 5, 4, 'I checked my spam folder as well, but there is no confirmation email.', '2026-02-24 16:10:00+00');
SELECT setval('ticket_comments_id_seq', (SELECT max(id) FROM ticket_comments));


-- Events
INSERT INTO ticket_events (id, ticket_id, actor_id, event_type, payload, created_at)
VALUES (1, 1, 3, 'CREATED', '{
  "ticketId": "1",
  "createdById": "3"
}', '2026-02-12 09:10:00+00'),

       (2, 1, 2, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "2"
       }', '2026-02-12 09:12:00+00'),

       (3, 1, 2, 'PRIORITY_CHANGED', '{
         "oldPriority": "MEDIUM",
         "newPriority": "HIGH"
       }', '2026-02-12 09:18:00+00'),

       (4, 1, 2, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', '2026-02-12 09:25:00+00'),

       (5, 1, 3, 'COMMENTED', '{
         "commentId": "1"
       }', '2026-02-12 10:05:00+00'),

       (6, 1, 3, 'COMMENTED', '{
         "commentId": "2"
       }', '2026-02-13 09:30:00+00'),

       (7, 2, 3, 'CREATED', '{
         "ticketId": "2",
         "createdById": "3"
       }', '2026-02-18 16:05:00+00'),

       (8, 3, 3, 'CREATED', '{
         "ticketId": "3",
         "createdById": "3"
       }', '2026-02-20 08:40:00+00'),

       (9, 4, 4, 'CREATED', '{
         "ticketId": "4",
         "createdById": "4"
       }', '2026-02-22 13:15:00+00'),

       (10, 4, 4, 'COMMENTED', '{
         "commentId": "3"
       }', '2026-02-22 13:25:00+00'),

       (11, 4, 2, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "2"
       }', '2026-02-22 14:00:00+00'),

       (12, 4, 2, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', '2026-02-22 14:05:00+00'),

       (13, 4, 2, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "WAITING_CUSTOMER"
       }', '2026-02-23 09:45:00+00'),

       (14, 4, 2, 'COMMENTED', '{
         "commentId": "4"
       }', '2026-02-23 09:45:00+00'),

       (15, 5, 4, 'CREATED', '{
         "ticketId": "5",
         "createdById": "4"
       }', '2026-02-24 15:30:00+00'),

       (16, 5, 4, 'COMMENTED', '{
         "commentId": "5"
       }', '2026-02-24 16:10:00+00'),

       (17, 5, 2, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "2"
       }', '2026-02-24 17:00:00+00'),

       (18, 5, 2, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', '2026-02-24 17:05:00+00'),

       (19, 5, 2, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "RESOLVED"
       }', '2026-02-25 10:20:00+00');
SELECT setval('ticket_events_id_seq', (SELECT max(id) FROM ticket_events));