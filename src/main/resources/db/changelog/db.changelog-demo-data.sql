--liquibase formatted sql

--changeset rollie:demo-users context:dev
INSERT INTO users (id, full_name, email, role, created_at, password, audit_created_by)
VALUES (1001, 'Lex Luthor', 'lex.luthor@ticketflow.dev', 'ADMIN', now() - interval '90 days', '{noop}123', 'demo-data'),
       (1002, 'Bruce Wayne', 'bruce.wayne@ticketflow.dev', 'AGENT', now() - interval '80 days', '{noop}123',
        'demo-data'),
       (1003, 'Diana Prince', 'diana.prince@ticketflow.dev', 'AGENT', now() - interval '75 days', '{noop}123',
        'demo-data'),
       (1004, 'Clark Kent', 'clark.kent@ticketflow.dev', 'CUSTOMER', now() - interval '70 days', '{noop}123',
        'demo-data'),
       (1005, 'Oliver Queen', 'oliver.queen@ticketflow.dev', 'CUSTOMER', now() - interval '60 days', '{noop}123',
        'demo-data');

SELECT setval('users_id_seq', GREATEST((SELECT max(id) FROM users), 1005));


--changeset rollie:demo-tickets context:dev
INSERT INTO tickets (id,
                     title,
                     description,
                     status,
                     priority,
                     created_by_id,
                     assigned_to_id,
                     created_at,
                     modified_at,
                     resolved_at,
                     version,
                     audit_created_by,
                     modified_by,
                     first_responded_at,
                     first_response_deadline,
                     resolution_deadline,
                     response_sla_status,
                     resolution_sla_status,
                     resolution_sla_paused_at)
VALUES (1001,
        'Cannot log in to the customer portal',
        'Customer cannot log in after resetting the password. The login page returns an invalid credentials message.',
        'NEW',
        'HIGH',
        1004,
        NULL,
        now() - interval '1 hour',
        now() - interval '1 hour',
        NULL,
        0,
        'clark.kent@ticketflow.dev',
        NULL,
        NULL,
        now() + interval '7 hours',
        now() + interval '23 hours',
        'ON_TRACK',
        'ON_TRACK',
        NULL),
       (1002,
        'Billing discrepancy on monthly invoice',
        'Customer reports that the last monthly invoice contains an unexpected additional charge.',
        'NEW',
        'MEDIUM',
        1004,
        NULL,
        now() - interval '2 days',
        now() - interval '2 days',
        NULL,
        0,
        'clark.kent@ticketflow.dev',
        NULL,
        NULL,
        now() - interval '1 day',
        now() + interval '1 day',
        'BREACHED',
        'ON_TRACK',
        NULL),
       (1003,
        'Payment page fails after card confirmation',
        'Customer reaches card confirmation, but the payment page displays a generic error after confirmation.',
        'IN_PROGRESS',
        'HIGH',
        1004,
        1002,
        now() - interval '6 hours',
        now() - interval '1 hour',
        NULL,
        0,
        'clark.kent@ticketflow.dev',
        'bruce.wayne@ticketflow.dev',
        now() - interval '1 hour',
        now() + interval '2 hours',
        now() + interval '18 hours',
        'MET',
        'ON_TRACK',
        NULL),
       (1004,
        'Production checkout is unavailable',
        'Several checkout attempts fail with a timeout. Customer reports that the issue blocks all purchases.',
        'IN_PROGRESS',
        'CRITICAL',
        1005,
        1003,
        now() - interval '12 hours',
        now() - interval '3 hours',
        NULL,
        0,
        'oliver.queen@ticketflow.dev',
        'diana.prince@ticketflow.dev',
        now() - interval '3 hours',
        now() - interval '10 hours',
        now() - interval '4 hours',
        'BREACHED',
        'BREACHED',
        NULL),
       (1005,
        'Refund request requires payment confirmation',
        'Customer requested a refund, but the support team needs the payment confirmation before proceeding.',
        'WAITING_CUSTOMER',
        'LOW',
        1005,
        1002,
        now() - interval '3 days',
        now() - interval '1 day',
        NULL,
        0,
        'oliver.queen@ticketflow.dev',
        'bruce.wayne@ticketflow.dev',
        now() - interval '2 days 18 hours',
        now() - interval '1 day',
        now() + interval '4 days',
        'MET',
        'PAUSED',
        now() - interval '1 day'),
       (1006,
        'Unable to change account email address',
        'Customer wants to update the email address, but the verification email is not received.',
        'WAITING_CUSTOMER',
        'MEDIUM',
        1004,
        1002,
        now() - interval '4 days',
        now() - interval '2 days',
        NULL,
        0,
        'clark.kent@ticketflow.dev',
        'bruce.wayne@ticketflow.dev',
        now() - interval '3 days 20 hours',
        now() - interval '3 days',
        now() - interval '1 day',
        'MET',
        'PAUSED',
        now() - interval '2 days'),
       (1007,
        'Invoice was not generated after payment',
        'Customer completed payment successfully, but the invoice was not generated in the portal.',
        'RESOLVED',
        'MEDIUM',
        1005,
        1002,
        now() - interval '2 days',
        now() - interval '6 hours',
        now() - interval '6 hours',
        0,
        'oliver.queen@ticketflow.dev',
        'bruce.wayne@ticketflow.dev',
        now() - interval '1 day 18 hours',
        now() - interval '1 day',
        now() + interval '1 day',
        'MET',
        'PAUSED',
        now() - interval '6 hours'),
       (1008,
        'Two-factor authentication code expired immediately',
        'Customer reports that the 2FA code expires immediately after being sent.',
        'RESOLVED',
        'HIGH',
        1004,
        1003,
        now() - interval '6 days',
        now() - interval '5 days',
        now() - interval '5 days',
        0,
        'clark.kent@ticketflow.dev',
        'diana.prince@ticketflow.dev',
        now() - interval '5 days 20 hours',
        now() - interval '5 days 16 hours',
        now() - interval '5 days',
        'MET',
        'PAUSED',
        now() - interval '5 days'),
       (1009,
        'Password reset issue was solved',
        'Customer had trouble resetting the password. The issue has already been solved and closed.',
        'CLOSED',
        'LOW',
        1004,
        1002,
        now() - interval '10 days',
        now() - interval '7 days',
        now() - interval '7 days',
        0,
        'clark.kent@ticketflow.dev',
        'bruce.wayne@ticketflow.dev',
        now() - interval '9 days 18 hours',
        now() - interval '8 days',
        now() - interval '3 days',
        'MET',
        'MET',
        NULL),
       (1010,
        'Critical API outage reported by customer',
        'Customer reported that the public API was unavailable during peak usage. The ticket is closed after late resolution.',
        'CLOSED',
        'CRITICAL',
        1005,
        1003,
        now() - interval '6 days',
        now() - interval '5 days',
        now() - interval '5 days',
        0,
        'oliver.queen@ticketflow.dev',
        'diana.prince@ticketflow.dev',
        now() - interval '5 days 20 hours',
        now() - interval '5 days 22 hours',
        now() - interval '5 days 16 hours',
        'BREACHED',
        'BREACHED',
        NULL);

SELECT setval('tickets_id_seq', GREATEST((SELECT max(id) FROM tickets), 1010));


--changeset rollie:demo-comments context:dev
INSERT INTO ticket_comments (id, ticket_id, author_id, body, created_at, audit_created_by)
VALUES (1001, 1003, 1004, 'The payment page fails right after my bank confirms the card payment.',
        now() - interval '5 hours 30 minutes', 'clark.kent@ticketflow.dev'),
       (1002, 1003, 1002, 'I am checking the payment logs now and will update you shortly.', now() - interval '1 hour',
        'bruce.wayne@ticketflow.dev'),
       (1003, 1004, 1005, 'This blocks all checkout attempts from our account. Please treat this as urgent.',
        now() - interval '11 hours 30 minutes', 'oliver.queen@ticketflow.dev'),
       (1004, 1004, 1003, 'We found timeout errors in the checkout integration and are investigating the root cause.',
        now() - interval '3 hours', 'diana.prince@ticketflow.dev'),
       (1005, 1005, 1005, 'I cancelled the subscription before renewal, but the payment was still taken.',
        now() - interval '2 days 23 hours', 'oliver.queen@ticketflow.dev'),
       (1006, 1005, 1002, 'Could you please upload the payment confirmation or invoice so we can verify the refund?',
        now() - interval '1 day', 'bruce.wayne@ticketflow.dev'),
       (1007, 1006, 1004, 'I tried two different email addresses, but I still do not receive the verification message.',
        now() - interval '3 days 22 hours', 'clark.kent@ticketflow.dev'),
       (1008, 1006, 1002, 'Please confirm the new email address you want to use so we can update it manually.',
        now() - interval '2 days', 'bruce.wayne@ticketflow.dev'),
       (1009, 1007, 1005, 'The payment is completed, but the invoice is missing from the portal.',
        now() - interval '1 day 20 hours', 'oliver.queen@ticketflow.dev'),
       (1010, 1007, 1002, 'The invoice has been regenerated and should now be visible in your account.',
        now() - interval '6 hours', 'bruce.wayne@ticketflow.dev'),
       (1011, 1008, 1004, 'The authentication code expires before I can submit it.', now() - interval '5 days 23 hours',
        'clark.kent@ticketflow.dev'),
       (1012, 1008, 1003, 'We adjusted the 2FA expiry configuration and the code should now remain valid.',
        now() - interval '5 days', 'diana.prince@ticketflow.dev');

SELECT setval('ticket_comments_id_seq', GREATEST((SELECT max(id) FROM ticket_comments), 1012));


--changeset rollie:demo-events context:dev
INSERT INTO ticket_events (id, ticket_id, actor_id, event_type, payload, created_at, audit_created_by)
VALUES (1001, 1001, 1004, 'CREATED', '{
  "ticketId": "1001",
  "createdById": "1004"
}', now() - interval '1 hour', 'clark.kent@ticketflow.dev'),

       (1002, 1002, 1004, 'CREATED', '{
         "ticketId": "1002",
         "createdById": "1004"
       }', now() - interval '2 days', 'clark.kent@ticketflow.dev'),

       (1003, 1003, 1004, 'CREATED', '{
         "ticketId": "1003",
         "createdById": "1004"
       }', now() - interval '6 hours', 'clark.kent@ticketflow.dev'),
       (1004, 1003, 1002, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1002"
       }', now() - interval '5 hours 45 minutes', 'bruce.wayne@ticketflow.dev'),
       (1005, 1003, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '5 hours 40 minutes', 'bruce.wayne@ticketflow.dev'),
       (1006, 1003, 1004, 'COMMENTED', '{
         "commentId": "1001"
       }', now() - interval '5 hours 30 minutes', 'clark.kent@ticketflow.dev'),
       (1007, 1003, 1002, 'COMMENTED', '{
         "commentId": "1002"
       }', now() - interval '1 hour', 'bruce.wayne@ticketflow.dev'),

       (1008, 1004, 1005, 'CREATED', '{
         "ticketId": "1004",
         "createdById": "1005"
       }', now() - interval '12 hours', 'oliver.queen@ticketflow.dev'),
       (1009, 1004, 1003, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1003"
       }', now() - interval '11 hours 30 minutes', 'diana.prince@ticketflow.dev'),
       (1010, 1004, 1003, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '11 hours 20 minutes', 'diana.prince@ticketflow.dev'),
       (1011, 1004, 1005, 'COMMENTED', '{
         "commentId": "1003"
       }', now() - interval '11 hours 30 minutes', 'oliver.queen@ticketflow.dev'),
       (1012, 1004, 1003, 'COMMENTED', '{
         "commentId": "1004"
       }', now() - interval '3 hours', 'diana.prince@ticketflow.dev'),

       (1013, 1005, 1005, 'CREATED', '{
         "ticketId": "1005",
         "createdById": "1005"
       }', now() - interval '3 days', 'oliver.queen@ticketflow.dev'),
       (1014, 1005, 1002, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1002"
       }', now() - interval '2 days 22 hours', 'bruce.wayne@ticketflow.dev'),
       (1015, 1005, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '2 days 21 hours', 'bruce.wayne@ticketflow.dev'),
       (1016, 1005, 1005, 'COMMENTED', '{
         "commentId": "1005"
       }', now() - interval '2 days 23 hours', 'oliver.queen@ticketflow.dev'),
       (1017, 1005, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "WAITING_CUSTOMER"
       }', now() - interval '1 day', 'bruce.wayne@ticketflow.dev'),
       (1018, 1005, 1002, 'COMMENTED', '{
         "commentId": "1006"
       }', now() - interval '1 day', 'bruce.wayne@ticketflow.dev'),

       (1019, 1006, 1004, 'CREATED', '{
         "ticketId": "1006",
         "createdById": "1004"
       }', now() - interval '4 days', 'clark.kent@ticketflow.dev'),
       (1020, 1006, 1002, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1002"
       }', now() - interval '3 days 23 hours', 'bruce.wayne@ticketflow.dev'),
       (1021, 1006, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '3 days 22 hours', 'bruce.wayne@ticketflow.dev'),
       (1022, 1006, 1004, 'COMMENTED', '{
         "commentId": "1007"
       }', now() - interval '3 days 22 hours', 'clark.kent@ticketflow.dev'),
       (1023, 1006, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "WAITING_CUSTOMER"
       }', now() - interval '2 days', 'bruce.wayne@ticketflow.dev'),
       (1024, 1006, 1002, 'COMMENTED', '{
         "commentId": "1008"
       }', now() - interval '2 days', 'bruce.wayne@ticketflow.dev'),

       (1025, 1007, 1005, 'CREATED', '{
         "ticketId": "1007",
         "createdById": "1005"
       }', now() - interval '2 days', 'oliver.queen@ticketflow.dev'),
       (1026, 1007, 1002, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1002"
       }', now() - interval '1 day 22 hours', 'bruce.wayne@ticketflow.dev'),
       (1027, 1007, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '1 day 21 hours', 'bruce.wayne@ticketflow.dev'),
       (1028, 1007, 1005, 'COMMENTED', '{
         "commentId": "1009"
       }', now() - interval '1 day 20 hours', 'oliver.queen@ticketflow.dev'),
       (1029, 1007, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "RESOLVED"
       }', now() - interval '6 hours', 'bruce.wayne@ticketflow.dev'),
       (1030, 1007, 1002, 'COMMENTED', '{
         "commentId": "1010"
       }', now() - interval '6 hours', 'bruce.wayne@ticketflow.dev'),

       (1031, 1008, 1004, 'CREATED', '{
         "ticketId": "1008",
         "createdById": "1004"
       }', now() - interval '6 days', 'clark.kent@ticketflow.dev'),
       (1032, 1008, 1003, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1003"
       }', now() - interval '5 days 23 hours', 'diana.prince@ticketflow.dev'),
       (1033, 1008, 1003, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '5 days 22 hours', 'diana.prince@ticketflow.dev'),
       (1034, 1008, 1004, 'COMMENTED', '{
         "commentId": "1011"
       }', now() - interval '5 days 23 hours', 'clark.kent@ticketflow.dev'),
       (1035, 1008, 1003, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "RESOLVED"
       }', now() - interval '5 days', 'diana.prince@ticketflow.dev'),
       (1036, 1008, 1003, 'COMMENTED', '{
         "commentId": "1012"
       }', now() - interval '5 days', 'diana.prince@ticketflow.dev'),

       (1037, 1009, 1004, 'CREATED', '{
         "ticketId": "1009",
         "createdById": "1004"
       }', now() - interval '10 days', 'clark.kent@ticketflow.dev'),
       (1038, 1009, 1002, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1002"
       }', now() - interval '9 days 20 hours', 'bruce.wayne@ticketflow.dev'),
       (1039, 1009, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '9 days 19 hours', 'bruce.wayne@ticketflow.dev'),
       (1040, 1009, 1002, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "RESOLVED"
       }', now() - interval '7 days', 'bruce.wayne@ticketflow.dev'),
       (1041, 1009, 1004, 'STATUS_CHANGED', '{
         "oldStatus": "RESOLVED",
         "newStatus": "CLOSED"
       }', now() - interval '6 days', 'clark.kent@ticketflow.dev'),

       (1042, 1010, 1005, 'CREATED', '{
         "ticketId": "1010",
         "createdById": "1005"
       }', now() - interval '6 days', 'oliver.queen@ticketflow.dev'),
       (1043, 1010, 1003, 'ASSIGNED', '{
         "previousAssigneeId": null,
         "assigneeId": "1003"
       }', now() - interval '5 days 23 hours', 'diana.prince@ticketflow.dev'),
       (1044, 1010, 1003, 'STATUS_CHANGED', '{
         "oldStatus": "NEW",
         "newStatus": "IN_PROGRESS"
       }', now() - interval '5 days 22 hours', 'diana.prince@ticketflow.dev'),
       (1045, 1010, 1003, 'STATUS_CHANGED', '{
         "oldStatus": "IN_PROGRESS",
         "newStatus": "RESOLVED"
       }', now() - interval '5 days', 'diana.prince@ticketflow.dev'),
       (1046, 1010, 1005, 'STATUS_CHANGED', '{
         "oldStatus": "RESOLVED",
         "newStatus": "CLOSED"
       }', now() - interval '4 days', 'oliver.queen@ticketflow.dev');

SELECT setval('ticket_events_id_seq', GREATEST((SELECT max(id) FROM ticket_events), 1046));

