<?php
// Written automatically by install.php. Keep private. Shown here only as a reference.
return [
    'community_id'   => 'my-mahalle',                 // public ID users type into the app
    'community_name' => 'محله من',                    // display name
    'admin_seed'     => 'BASE64_32_BYTE_SEED',        // signs writer certificates; also typed into the admin phone
    'admin_token'    => 'LONG_RANDOM_STRING',         // required by manage.php
    'directory_url'  => 'https://hhi1.ir/hamresan-dir/index.php',
    'db_path'        => __DIR__ . '/data/hamresan.sqlite',
    'cert_days'      => 365,
];
