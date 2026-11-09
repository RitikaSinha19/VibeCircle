-- DEV ONLY test data for discovery. NOT a Flyway migration; run it by hand with psql.
-- All test users have password: StrongPass123   (login as test_a)
--
-- Expected for test_a (Bengaluru; Cricket, Singing, Programming, Travelling):
--   test_d  100.0%  (identical interests)
--   test_b   40.0%  (shares Cricket + Programming; union = 5)
-- Must NOT appear: test_c (other city), test_e (no shared interest), test_f (private profile),
--                  test_g (blocked), test_h (pending request), test_i (already a friend)

INSERT INTO users (username, email, password_hash, full_name, date_of_birth, role, status) VALUES
 ('test_a','test_a@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test A','1999-01-01','USER','ACTIVE'),
 ('test_b','test_b@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test B','1999-01-01','USER','ACTIVE'),
 ('test_c','test_c@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test C','1999-01-01','USER','ACTIVE'),
 ('test_d','test_d@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test D','1999-01-01','USER','ACTIVE'),
 ('test_e','test_e@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test E','1999-01-01','USER','ACTIVE'),
 ('test_f','test_f@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test F','1999-01-01','USER','ACTIVE'),
 ('test_g','test_g@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test G','1999-01-01','USER','ACTIVE'),
 ('test_h','test_h@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test H','1999-01-01','USER','ACTIVE'),
 ('test_i','test_i@example.com','$2a$12$yuvJJwgh9Mh5nCf8yg6vTOmvRD20jsNGQLyMRRLSKmRVouCDkoohK','Test I','1999-01-01','USER','ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO user_profiles (user_id, city_id, bio, profile_visibility, discoverable)
SELECT u.id, (SELECT id FROM cities WHERE name = v.city), v.bio, v.vis, true
FROM (VALUES
  ('test_a','Bengaluru','Cricket and code','PUBLIC'),
  ('test_b','Bengaluru','Photographer who codes','PUBLIC'),
  ('test_c','Kolkata','Same interests, different city','PUBLIC'),
  ('test_d','Bengaluru','Identical interests to A','PUBLIC'),
  ('test_e','Bengaluru','Gamer','PUBLIC'),
  ('test_f','Bengaluru','Private profile','PRIVATE'),
  ('test_g','Bengaluru','Blocked by A','PUBLIC'),
  ('test_h','Bengaluru','Has a pending request from A','PUBLIC'),
  ('test_i','Bengaluru','Already friends with A','PUBLIC')
) AS v(username, city, bio, vis)
JOIN users u ON u.username = v.username
ON CONFLICT (user_id) DO UPDATE
  SET city_id = EXCLUDED.city_id, bio = EXCLUDED.bio, profile_visibility = EXCLUDED.profile_visibility;

INSERT INTO user_interests (user_id, interest_id)
SELECT u.id, i.id
FROM (VALUES
  ('test_a','Cricket'),('test_a','Singing'),('test_a','Programming'),('test_a','Travelling'),
  ('test_b','Cricket'),('test_b','Programming'),('test_b','Photography'),
  ('test_c','Cricket'),('test_c','Singing'),('test_c','Programming'),
  ('test_d','Cricket'),('test_d','Singing'),('test_d','Programming'),('test_d','Travelling'),
  ('test_e','Gaming'),('test_e','Anime'),
  ('test_f','Cricket'),('test_f','Programming'),
  ('test_g','Cricket'),('test_g','Programming'),
  ('test_h','Cricket'),('test_h','Programming'),
  ('test_i','Cricket'),('test_i','Programming')
) AS v(username, interest)
JOIN users u ON u.username = v.username
JOIN interests i ON i.name = v.interest
ON CONFLICT DO NOTHING;

INSERT INTO blocks (blocker_id, blocked_id)
SELECT a.id, g.id FROM users a, users g WHERE a.username = 'test_a' AND g.username = 'test_g'
ON CONFLICT DO NOTHING;

INSERT INTO friend_requests (sender_id, receiver_id, status)
SELECT a.id, h.id, 'PENDING' FROM users a, users h WHERE a.username = 'test_a' AND h.username = 'test_h'
ON CONFLICT DO NOTHING;

INSERT INTO friendships (user_low_id, user_high_id)
SELECT LEAST(a.id, i.id), GREATEST(a.id, i.id) FROM users a, users i
WHERE a.username = 'test_a' AND i.username = 'test_i'
ON CONFLICT DO NOTHING;

-- Cleanup when finished (everything cascades):
-- DELETE FROM users WHERE username LIKE 'test\_%';
