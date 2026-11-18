export type User = {
  id: string;
  name: string;
  email: string;
  role: "ADMIN" | "CASHIER" | "WAREHOUSE";
};
export type Category = { id: string; name: string };
export type Product = {
  id: string;
  code: string;
  name: string;
  categoryId: string;
  price: number;
  stock: number;
};
export type Sale = {
  id: string;
  cashierId: string;
  cashierName: string;
  createdAt: string;
  items: {
    productId: string;
    code: string;
    name: string;
    quantity: number;
    unitPrice: number;
    subtotal: number;
  }[];
  total: number;
  tendered: number;
  change: number;
  paymentMethod: string;
};
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method: body === undefined ? "GET" : "POST",
      credentials: "same-origin",
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new Error(
      "No pudimos conectar con el servidor. Comprueba que esté disponible e inténtalo de nuevo.",
    );
  }
  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      data?.message || "No se pudo completar la operación.",
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export const money = (value: number) =>
  new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(value);
export const date = (value: string) =>
  new Intl.DateTimeFormat("es-CO", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
