CREATE TABLE Pago (
  id_pago NUMERIC(2) NOT NULL,
  pago VARCHAR(15),
  PRIMARY KEY(id_pago)
);

CREATE TABLE Estado (
  id_estado NUMERIC(2) NOT NULL,
  estado VARCHAR(15),
  PRIMARY KEY(id_estado)
);

CREATE TABLE Rol (
  id_rol NUMERIC(2) NOT NULL,
  rol VARCHAR(30),
  PRIMARY KEY(id_rol)
);

CREATE TABLE Clientes (
  id_cliente NUMERIC(8) NOT NULL,
  nombre VARCHAR(50),
  ap_paterno VARCHAR(50),
  ap_materno VARCHAR(50),
  telefono NUMERIC(10),
  correo VARCHAR(50),
  PRIMARY KEY(id_cliente)
);

CREATE TABLE Proveedor (
  id_proveedor NUMERIC(8) NOT NULL,
  nombre VARCHAR(50) NOT NULL,
  telefono NUMERIC(15),
  correo VARCHAR(50),
  PRIMARY KEY(id_proveedor)
);

CREATE TABLE Pedidos (
  id_pedido NUMERIC(13) NOT NULL,
  id_proveedor NUMERIC(8) NOT NULL,
  pago NUMERIC(8,2) NULL,
  fecha DATE,
  PRIMARY KEY(id_pedido),
  CONSTRAINT fk_ped_prov FOREIGN KEY (id_proveedor) 
  REFERENCES Proveedor(id_proveedor) 
);

CREATE TABLE Productos (
  id_producto NUMERIC(13) NOT NULL,
  id_proveedor NUMERIC(8) NOT NULL,
  nombre VARCHAR(30) NULL,
  precio_compra NUMERIC(7,2) NULL,
  precio_venta NUMERIC(7,2) NULL,
  cantidad NUMERIC(5) NULL,
  stock_minimo NUMERIC(2) NULL,
  PRIMARY KEY(id_producto),
  CONSTRAINT fk_produ_prov FOREIGN KEY (id_proveedor) 
  REFERENCES Proveedor(id_proveedor)
);

CREATE TABLE Empleados (
  id_empleado NUMERIC(3) NOT NULL,
  id_rol NUMERIC(1) NOT NULL,
  Nombre VARCHAR(50),
  Ap_paterno VARCHAR(50),
  Ap_materno VARCHAR(50),
  telefono NUMERIC(12),
  edad NUMERIC(2),
  CURP VARCHAR(18),
  RFC VARCHAR(13),
  correo VARCHAR(50),
  PRIMARY KEY(id_empleado),
  CONSTRAINT fk_emp_rol FOREIGN KEY (id_rol) 
  REFERENCES Rol(id_rol)
);

CREATE TABLE Caja (
  id_caja NUMERIC(13) NOT NULL,
  id_estado NUMERIC(1) NOT NULL,
  id_empleado NUMERIC(3) NOT NULL,
  fecha DATE,
  fondo_inicial NUMERIC(8,2),
  conteo_fisico NUMERIC(8,2),
  diferencia NUMERIC(8,2),
  PRIMARY KEY(id_caja),
  CONSTRAINT fk_caja_est FOREIGN KEY (id_estado) 
  REFERENCES Estado(id_estado),
  CONSTRAINT fk_caja_emp FOREIGN KEY (id_empleado) 
  REFERENCES Empleados(id_empleado)
);

CREATE TABLE Ventas (
  id_venta NUMERIC(15) NOT NULL,
  id_pago NUMERIC(1) NOT NULL,
  id_empleado NUMERIC(3) NOT NULL,
  fecha DATE,
  total NUMERIC(8,2),
  PRIMARY KEY(id_venta),
  CONSTRAINT fk_vent_pago FOREIGN KEY (id_pago) 
  REFERENCES Pago(id_pago),
  CONSTRAINT fk_vent_emp FOREIGN KEY (id_empleado) 
  REFERENCES Empleados(id_empleado)
);

CREATE TABLE Detalles_venta (
  id_producto NUMERIC(13) NOT NULL,
  id_venta NUMERIC(15) NOT NULL,
  precio_unitario NUMERIC(8,2),
  subtotal NUMERIC(8,2),
  cantidad NUMERIC(4),
  PRIMARY KEY(id_producto, id_venta),
  CONSTRAINT fk_detv_prod FOREIGN KEY (id_producto) 
  REFERENCES Productos(id_producto),
  CONSTRAINT fk_detv_vent FOREIGN KEY (id_venta) 
  REFERENCES Ventas(id_venta)
);

CREATE TABLE Productos_x_Pedidos (
  id_producto NUMERIC(13) NOT NULL,
  id_pedido NUMERIC(13) NOT NULL,
  cantidad NUMERIC(4),
  precio NUMERIC(8,2),
  subtotal NUMERIC(8,2),
  PRIMARY KEY(id_producto, id_pedido),
  CONSTRAINT fk_pxp_prod FOREIGN KEY (id_producto) 
  REFERENCES Productos(id_producto),
  CONSTRAINT fk_pxp_ped FOREIGN KEY (id_pedido) 
  REFERENCES Pedidos(id_pedido)
);

CREATE TABLE Adeudos (
  id_adeudos NUMERIC(13) NOT NULL,
  id_estado NUMERIC(1) NOT NULL,
  id_cliente NUMERIC(8) NOT NULL,
  id_venta NUMERIC(15) NOT NULL,
  fecha_limite DATE,
  monto_pendiente NUMERIC(8,2),
  PRIMARY KEY(id_adeudos),
  CONSTRAINT fk_ade_est FOREIGN KEY (id_estado) 
  REFERENCES Estado(id_estado),
  CONSTRAINT fk_ade_cli FOREIGN KEY (id_cliente) 
  REFERENCES Clientes(id_cliente),
  CONSTRAINT fk_ade_vent FOREIGN KEY (id_venta) 
  REFERENCES Ventas(id_venta)
);


