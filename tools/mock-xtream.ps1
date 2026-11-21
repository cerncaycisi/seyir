# Local Xtream Codes / M3U mock for development. Serves only public, freely licensed test media.
# Login: test / test. From the emulator, run `adb reverse tcp:8099 tcp:8099` and use http://localhost:8099
param([int]$Port = 8099)

$sintel = 'https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4'
$tsClip = 'https://test-streams.mux.dev/x36xhzz/url_0/url_462/193039199_mp4_h264_aac_hd_7.ts'
$muxHls = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
$appleHls = 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8'

function B64([string]$s) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($s)) }

function Api([string]$action) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    switch ($action) {
        '' { return '{"user_info":{"auth":1,"status":"Active","username":"test"},"server_info":{"url":"localhost"}}' }
        'get_live_categories' { return '[{"category_id":"1","category_name":"Haber"},{"category_id":"2","category_name":"Belgesel"}]' }
        'get_live_streams' {
            return '[{"stream_id":101,"name":"Test Kanal 1","stream_icon":"https://picsum.photos/seed/kanal1/320/180","category_id":"1","epg_channel_id":"t1"},' +
                   '{"stream_id":102,"name":"Test Kanal 2","stream_icon":null,"category_id":"1","epg_channel_id":""},' +
                   '{"stream_id":103,"name":"Belgesel Test","stream_icon":"","category_id":"2"}]'
        }
        'get_vod_categories' { return '[{"category_id":"10","category_name":"Animasyon"},{"category_id":"11","category_name":"Belgesel"}]' }
        'get_vod_info' { return '{"info":{"plot":"Yalnız bir kız, yaraladığı ejderha yavrusunu ararken tehlikeli bir yolculuğa çıkar. Blender Vakfı yapımı açık lisanslı kısa film.","genre":"Animasyon, Fantastik","duration":"00:14:48","releasedate":"2010-09-27","rating":"7.6","backdrop_path":["https://picsum.photos/seed/backdrop/1280/720"]}}' }
        'get_vod_streams' {
            return '[{"stream_id":201,"name":"Sintel (Fragman)","stream_icon":"https://picsum.photos/seed/sintel/400/600","rating":"7.6","year":"2010","category_id":"10","container_extension":"mp4"},' +
                   '{"stream_id":202,"name":"Sintel, Tekrar","stream_icon":"https://picsum.photos/seed/tekrar/400/600","rating":"6.9","category_id":"10","container_extension":"mp4"},' +
                   '{"stream_id":203,"name":"Afişsiz Film","stream_icon":"","category_id":"11","container_extension":"mp4"}]'
        }
        'get_series_categories' { return '[{"category_id":"20","category_name":"Test Dizileri"}]' }
        'get_series' { return '[{"series_id":301,"name":"Test Dizisi","cover":"https://picsum.photos/seed/dizi/400/600","plot":"Uçtan uca test için örnek dizi.","rating":"8.1","releaseDate":"2024-03-01","genre":"Dram","backdrop_path":["https://picsum.photos/seed/dizibg/1280/720"],"category_id":"20"}]' }
        'get_series_info' {
            return '{"info":{"name":"Test Dizisi","cover":"https://picsum.photos/seed/dizi/400/600","plot":"Uçtan uca test için örnek dizi.","rating":"8.1","genre":"Dram","releaseDate":"2024-03-01","backdrop_path":["https://picsum.photos/seed/dizibg/1280/720"]},"episodes":{' +
                   '"1":[{"id":"3011","episode_num":1,"title":"İlk Bölüm","container_extension":"mp4","season":1},' +
                   '{"id":"3012","episode_num":2,"title":"İkinci Bölüm","container_extension":"mp4","season":1}],' +
                   '"2":[{"id":"3021","episode_num":1,"title":"Yeni Sezon","container_extension":"mp4","season":2}]}}'
        }
        'get_short_epg' {
            $a = B64 'Test Haberleri'; $b = B64 'Akşam Belgeseli'
            return "{`"epg_listings`":[{`"title`":`"$a`",`"start_timestamp`":`"$($now - 600)`",`"stop_timestamp`":`"$($now + 1200)`"}," +
                   "{`"title`":`"$b`",`"start_timestamp`":`"$($now + 1200)`",`"stop_timestamp`":`"$($now + 4800)`"}]}"
        }
        default { return '[]' }
    }
}

$m3u = @"
#EXTM3U
#EXTINF:-1 tvg-id="mux" group-title="Test HLS",Mux Test HLS
$muxHls
#EXTINF:-1 tvg-id="apple" group-title="Test HLS",Apple BipBop
$appleHls
#EXTINF:-1 group-title="Bozuk",Bozuk Kanal
http://localhost:$Port/broken.m3u8
#EXTINF:-1 group-title="Filmler",Sintel (Fragman)
$sintel
"@

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Start()
Write-Host "mock ready on http://localhost:$Port"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $req = $ctx.Request
    $res = $ctx.Response
    $path = $req.Url.AbsolutePath
    Write-Host "$(Get-Date -Format HH:mm:ss) $($req.HttpMethod) $($req.Url.PathAndQuery)"
    $body = $null
    $redirect = $null
    $type = 'application/json; charset=utf-8'

    if ($path -eq '/player_api.php') {
        if ($req.QueryString['username'] -ne 'test' -or $req.QueryString['password'] -ne 'test') {
            $body = '{"user_info":{"auth":0}}'
        } else {
            $body = Api ([string]$req.QueryString['action'])
        }
    } elseif ($path -like '/live/*') {
        $redirect = $tsClip
    } elseif ($path -like '/movie/*' -or $path -like '/series/*') {
        $redirect = $sintel
    } elseif ($path -eq '/test.m3u') {
        $type = 'audio/x-mpegurl; charset=utf-8'
        $body = $m3u
    } else {
        $res.StatusCode = 404
        $body = ''
    }

    if ($redirect) {
        $res.Redirect($redirect)
    } else {
        $bytes = [Text.Encoding]::UTF8.GetBytes($body)
        $res.ContentType = $type
        $res.OutputStream.Write($bytes, 0, $bytes.Length)
    }
    $res.Close()
}
