import { describe, expect, it } from "vitest";
import { env } from "./env";

describe("env", () => {
  it("vitest 注入的 NEXT_PUBLIC_API_BASE_URL 可通过校验", () => {
    expect(env.NEXT_PUBLIC_API_BASE_URL).toBe("http://api.test");
  });
});
