package com.example.game

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.hypot

const val htmlString = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0">
    <style>
        body { margin: 0; overflow: hidden; background-color: #000; touch-action: none; }
        canvas { display: block; width: 100vw; height: 100vh; }
    </style>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
</head>
<body>
    <script>
        window.moveX = 0; window.moveY = 0;
        window.fpsCap = 60;
        let yaw = 0; let pitch = 0;

        function updateMove(mx, my) { 
            if(isNaN(mx) || isNaN(my)) return;
            window.moveX = mx; window.moveY = my; 
        }
        
        function applyLook(dx, dy) {
            if(isNaN(dx) || isNaN(dy)) return;
            yaw -= dx; pitch -= dy;
            pitch = Math.max(-Math.PI/2 + 0.05, Math.min(Math.PI/2 - 0.05, pitch));
            if(camera) {
                camera.rotation.y = yaw; camera.rotation.x = pitch;
            }
        }

        function setFps(fps) { window.fpsCap = fps; }

        const scene = new THREE.Scene();
        scene.fog = new THREE.FogExp2(0xdcc889, 0.08); // Backrooms yellow fog
        
        const camera = new THREE.PerspectiveCamera(80, window.innerWidth / window.innerHeight, 0.01, 50);
        camera.rotation.order = 'YXZ';
        
        const renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: "high-performance" });
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.setPixelRatio(window.devicePixelRatio > 1.5 ? 1.5 : window.devicePixelRatio); 
        document.body.appendChild(renderer.domElement);

        window.addEventListener('resize', () => {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        });

        scene.add(new THREE.AmbientLight(0xffffff, 0.35));
        const pointLight = new THREE.PointLight(0xffebb5, 0.8, 12);
        scene.add(pointLight);

        // Procedural Textures
        function createTex(w, h, drawFn) {
            const c = document.createElement('canvas');
            c.width = w; c.height = h;
            drawFn(c.getContext('2d'));
            const tex = new THREE.CanvasTexture(c);
            tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
            return tex;
        }

        const wallTex = createTex(128, 128, ctx => {
            ctx.fillStyle = '#dcc889'; ctx.fillRect(0,0,128,128);
            ctx.fillStyle = '#b5a96a';
            for(let i=0; i<128; i+=16) ctx.fillRect(i, 0, 2, 128);
            ctx.fillStyle = '#8f8551';
            for(let i=0; i<128; i+=32) for(let j=0; j<128; j+=64) ctx.fillRect(i+8, j+32, 4, 16);
        });

        const floorTex = createTex(256, 256, ctx => {
            ctx.fillStyle = '#1e1e24'; ctx.fillRect(0,0,256,256);
            for(let i=0; i<1500; i++) {
                ctx.fillStyle = Math.random() > 0.5 ? '#2c3e50' : '#141418';
                ctx.fillRect(Math.random()*256, Math.random()*256, 4, 4);
            }
        });

        const ceilTex = createTex(128, 128, ctx => {
            ctx.fillStyle = '#dbd9cd'; ctx.fillRect(0,0,128,128);
            ctx.strokeStyle = '#aaaaaa'; ctx.lineWidth=2; ctx.strokeRect(0,0,128,128);
            ctx.fillStyle = '#ffffff'; ctx.fillRect(10, 10, 108, 108); 
        });

        // Infinity Maze inspired by Level 0
        const mapW = 100, mapH = 100;
        floorTex.repeat.set(mapW/2, mapH/2);
        ceilTex.repeat.set(mapW*2, mapH*2);
        
        const map = new Uint8Array(mapW * mapH);
        map.fill(1); // 1 = wall
        function carve(x, y) {
            map[y*mapW + x] = 0;
            const dirs = [[0,-2],[0,2],[-2,0],[2,0]].sort(() => Math.random() - 0.5);
            for(let d of dirs) {
                const nx = x + d[0], ny = y + d[1];
                if(nx > 0 && nx < mapW-1 && ny > 0 && ny < mapH-1 && map[ny*mapW + nx] === 1) {
                    map[(y + d[1]/2)*mapW + (x + d[0]/2)] = 0;
                    carve(nx, ny);
                }
            }
        }
        carve(1, 1);
        
        // Spawn rooms randomly
        for(let i=0; i<40; i++) {
            let rx = Math.floor(Math.random()*(mapW-8))+2;
            let ry = Math.floor(Math.random()*(mapH-8))+2;
            let rw = Math.floor(Math.random()*5)+4;
            let rh = Math.floor(Math.random()*5)+4;
            for(let cy=ry; cy<ry+rh; cy++) for(let cx=rx; cx<rx+rw; cx++) map[cy*mapW + cx] = 0;
        }
        
        // Ensure starting area clear
        map[1*mapW+1] = 0; map[1*mapW+2] = 0; map[2*mapW+1] = 0;

        let wallCount = 0;
        for(let i=0; i<mapW*mapH; i++) if(map[i] === 1) wallCount++;
        
        const walls = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 3, 1), new THREE.MeshLambertMaterial({map: wallTex}), wallCount);
        const dummy = new THREE.Object3D();
        let idx = 0;
        for(let y=0; y<mapH; y++) {
            for(let x=0; x<mapW; x++) {
                if(map[y*mapW + x] === 1) {
                    dummy.position.set(x, 1.5, y); dummy.updateMatrix();
                    walls.setMatrixAt(idx++, dummy.matrix);
                }
            }
        }
        scene.add(walls);

        const floor = new THREE.Mesh(new THREE.PlaneGeometry(mapW, mapH), new THREE.MeshLambertMaterial({map: floorTex}));
        floor.rotation.x = -Math.PI/2; floor.position.set(mapW/2 - 0.5, 0, mapH/2 - 0.5); scene.add(floor);

        const ceil = new THREE.Mesh(new THREE.PlaneGeometry(mapW, mapH), new THREE.MeshLambertMaterial({map: ceilTex}));
        ceil.rotation.x = Math.PI/2; ceil.position.set(mapW/2 - 0.5, 3, mapH/2 - 0.5); scene.add(ceil);

        camera.position.set(1, 1.2, 1);
        const vel = new THREE.Vector3();
        let then = performance.now();

        function checkCol(pos) {
            const r = 0.25;
            const corners = [[pos.x-r, pos.z-r], [pos.x+r, pos.z-r], [pos.x-r, pos.z+r], [pos.x+r, pos.z+r]];
            for(let c of corners) {
                const mx = Math.round(c[0]), mz = Math.round(c[1]);
                if(mx >= 0 && mx < mapW && mz >= 0 && mz < mapH) {
                    if(map[mz * mapW + mx] === 1) return true;
                }
            }
            return false;
        }

        function animate(now) {
            requestAnimationFrame(animate);
            const fpsInterval = window.fpsCap === 144 ? 0 : 1000 / window.fpsCap;
            const elapsed = now - then;
            if(elapsed < fpsInterval) return;
            
            let dt = elapsed / 1000;
            dt = Math.min(dt, 0.05); 
            then = now - (elapsed % fpsInterval);

            vel.set(window.moveX, 0, window.moveY);
            vel.applyAxisAngle(new THREE.Vector3(0,1,0), yaw);
            const speed = 4.0; 
            const px = camera.position.x + vel.x * speed * dt;
            const pz = camera.position.z + vel.z * speed * dt;
            
            if(!checkCol(new THREE.Vector3(px, camera.position.y, camera.position.z))) camera.position.x = px;
            if(!checkCol(new THREE.Vector3(camera.position.x, camera.position.y, pz))) camera.position.z = pz;

            // Simple head bob
            const isMoving = Math.abs(window.moveX) > 0.01 || Math.abs(window.moveY) > 0.01;
            if(isMoving) camera.position.y = 1.2 + Math.sin(now * 0.012) * 0.06;
            else camera.position.y += (1.2 - camera.position.y) * 0.1;

            pointLight.position.copy(camera.position);
            renderer.render(scene, camera);
        }
        requestAnimationFrame(animate);
    </script>
</body>
</html>
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var fpsCap by remember { mutableIntStateOf(60) }

    var isDraggingLeft by remember { mutableStateOf(false) }
    var joyCenter by remember { mutableStateOf(Offset.Zero) }
    var joyCurrent by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(null, htmlString, "text/html", "UTF-8", null)
                    webView = this
                }
            },
            update = {
                it.evaluateJavascript("setFps($fpsCap);", null)
            }
        )

        // Overlay cho Joystick trái (Move) và khu vực phải rỗng (dành cho Vuốt look)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var leftTouchId: Long? = null
                    var rightTouchId: Long? = null
                    
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val isLeft = change.position.x < size.width / 2

                                if (change.pressed && !change.previousPressed) {
                                    if (isLeft && leftTouchId == null) {
                                        leftTouchId = change.id.value
                                        joyCenter = change.position
                                        joyCurrent = change.position
                                        isDraggingLeft = true
                                        change.consume()
                                    } else if (!isLeft && rightTouchId == null) {
                                        rightTouchId = change.id.value
                                        change.consume()
                                    }
                                } else if (change.pressed && change.previousPressed) {
                                    if (change.id.value == leftTouchId) {
                                        joyCurrent = change.position
                                        val dx = joyCurrent.x - joyCenter.x
                                        val dy = joyCurrent.y - joyCenter.y
                                        val maxR = 150f
                                        val dist = hypot(dx, dy)
                                        if (dist.isNaN() || dx.isNaN() || dy.isNaN()) return@forEach
                                        
                                        val mx = if(dist > maxR) dx*(maxR/dist) else dx
                                        val my = if(dist > maxR) dy*(maxR/dist) else dy
                                        
                                        webView?.evaluateJavascript("updateMove(${mx / maxR}, ${my / maxR})", null)
                                        change.consume()
                                    } else if (change.id.value == rightTouchId) {
                                        val dx = change.position.x - change.previousPosition.x
                                        val dy = change.position.y - change.previousPosition.y
                                        if (dx.isNaN() || dy.isNaN()) return@forEach
                                        
                                        val sens = 0.005f
                                        webView?.evaluateJavascript("applyLook(${dx * sens}, ${dy * sens})", null)
                                        change.consume()
                                    }
                                } else if (!change.pressed && change.previousPressed) {
                                    if (change.id.value == leftTouchId) {
                                        leftTouchId = null
                                        isDraggingLeft = false
                                        webView?.evaluateJavascript("updateMove(0, 0)", null)
                                        change.consume()
                                    } else if (change.id.value == rightTouchId) {
                                        rightTouchId = null
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (isDraggingLeft) {
                val dx = joyCurrent.x - joyCenter.x
                val dy = joyCurrent.y - joyCenter.y
                val maxR = 150f
                val dist = hypot(dx, dy)
                val mx = if(dist > maxR) dx*(maxR/dist) else dx
                val my = if(dist > maxR) dy*(maxR/dist) else dy

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color.White.copy(alpha=0.2f), radius = maxR, center = joyCenter)
                    drawCircle(color = Color.White.copy(alpha=0.6f), radius = 60f, center = Offset(joyCenter.x + mx, joyCenter.y + my))
                }
            }
        }

        // Crosshair
        Box(
            modifier = Modifier.align(Alignment.Center).size(6.dp).background(Color.White.copy(alpha=0.7f), CircleShape)
        )

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }

        if (showSettings) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("FPS Cap: ${if(fpsCap==144) "Max (144)" else fpsCap}", color = Color.White)

                    Row(modifier = Modifier.padding(vertical = 16.dp)) {
                        listOf(30, 60, 90, 120, 144).forEach { fps ->
                            Button(
                                onClick = {
                                    fpsCap = fps
                                    webView?.evaluateJavascript("setFps($fps)", null)
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (fpsCap == fps) Color.White else Color.DarkGray,
                                    contentColor = if (fpsCap == fps) Color.Black else Color.White
                                )
                            ) {
                                Text("$fps")
                            }
                        }
                    }

                    Button(onClick = { showSettings = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
