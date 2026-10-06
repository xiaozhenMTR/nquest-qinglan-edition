<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
$file = __DIR__ . '/questqp.json';
if (file_exists($file)) {
    readfile($file);
} else {
    echo '{}';
}
