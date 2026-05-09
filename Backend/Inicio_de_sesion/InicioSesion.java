<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Inicio de Sesión</title>

<style>

body{
font-family:Arial;
background:#f4f4f4;
margin:0;
display:flex;
justify-content:center;
align-items:center;
height:100vh;
}

.login{
background:white;
padding:30px;
border-radius:8px;
box-shadow:0 0 10px rgba(0,0,0,0.2);
text-align:center;
width:300px;
}

input{
width:90%;
padding:10px;
margin:10px 0;
}

button{
padding:10px 15px;
cursor:pointer;
}

.mensaje{
color:red;
font-weight:bold;
}

.menu{
display:none;
margin-top:20px;
}

</style>
</head>

<body>

<div class="login">

<h2>Inicio de Sesión</h2>

<input type="text" id="usuario" placeholder="Nombre de usuario">

<input type="password" id="password" placeholder="Contraseña">

<button onclick="iniciarSesion()">Iniciar Sesión</button>

<p id="mensaje" class="mensaje"></p>

<div id="menu" class="menu">
<h3 id="rolUsuario"></h3>
<p>Menú principal del sistema</p>
</div>

</div>

<script>



function iniciarSesion() {
    let correo = document.getElementById("usuario").value; 
    let password = document.getElementById("password").value;
    let mensaje = document.getElementById("mensaje");
    
    mensaje.innerText = "Verificando...";

    fetch('LoginServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'correo=' + encodeURIComponent(correo) + '&password=' + encodeURIComponent(password)
    })
    .then(response => response.text())
    .then(resultado => {
    let id = resultado.trim();
    console.log("ID de Rol recibido:", id);

    if (id === "1" || id === "3") { 
        window.location.href = "menu_principal.html"; 
    } 
    else if (id === "2") { 
        window.location.href = "menu_empleado.html";
    } 
    else if (id === "error") {
        mensaje.innerText = "Correo o contraseña incorrectos";
    } 
    else {
        mensaje.innerText = "Error en la respuesta del servidor";
    }
    })
    .catch(error => {
        mensaje.innerText = "Error de red";
        console.error(error);
    });
}

</script>

</body>
</html>
