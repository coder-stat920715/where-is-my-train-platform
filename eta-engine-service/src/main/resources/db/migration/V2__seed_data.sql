-- Seed data: Indian Railways corridor
INSERT INTO stations (code, name, latitude, longitude) VALUES
 ('HWH',  'Howrah Junction',        22.5839, 88.3425),
 ('KGP',  'Kharagpur Junction',     22.3396, 87.3202),
 ('BLS',  'Balasore',               21.4934, 86.9135),
 ('CTC',  'Cuttack Junction',       20.4702, 85.8760),
 ('BBS',  'Bhubaneswar',            20.2644, 85.8441),
 ('PURI', 'Puri',                   19.7973, 85.8246),
 ('MAS',  'Chennai Central',        13.0827, 80.2757);

INSERT INTO trains (number, name, train_type, origin_code, destination_code, runs_on, max_speed_kmph) VALUES
 ('22895', 'Puri Vande Bharat Exp', 'VANDE_BHARAT', 'HWH', 'PURI', 'MON,TUE,WED,THU,FRI,SAT,SUN', 130),
 ('12841', 'Coromandel Express',    'SUPERFAST',    'HWH', 'MAS',  'MON,TUE,WED,THU,FRI,SAT,SUN', 130);

-- 22895 Puri Vande Bharat Exp (illustrative timetable)
INSERT INTO train_schedule (train_number, seq, station_code, sch_arr, sch_dep, day_offset, platform, distance_km) VALUES
 ('22895', 0, 'HWH',  NULL,    '06:10', 0, '8',   0),
 ('22895', 1, 'KGP',  '07:25', '07:27', 0, '3',   116),
 ('22895', 2, 'BLS',  '08:50', '08:52', 0, '1',   233),
 ('22895', 3, 'CTC',  '10:15', '10:17', 0, '1',   384),
 ('22895', 4, 'BBS',  '10:55', '10:57', 0, '2',   438),
 ('22895', 5, 'PURI', '12:15', NULL,    0, '1',   500);

-- 12841 Coromandel Express (illustrative timetable; continues to Chennai next day)
INSERT INTO train_schedule (train_number, seq, station_code, sch_arr, sch_dep, day_offset, platform, distance_km) VALUES
 ('12841', 0, 'HWH',  NULL,    '14:50', 0, '24',  0),
 ('12841', 1, 'KGP',  '16:30', '16:35', 0, '2',   116),
 ('12841', 2, 'BLS',  '18:07', '18:09', 0, '2',   233),
 ('12841', 3, 'CTC',  '19:50', '19:55', 0, '3',   384),
 ('12841', 4, 'BBS',  '20:35', '20:40', 0, '1',   438),
 ('12841', 5, 'MAS',  '17:15', NULL,    1, '5',   1662);
